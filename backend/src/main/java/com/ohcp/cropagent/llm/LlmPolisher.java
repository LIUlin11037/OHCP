package com.ohcp.cropagent.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 语言组织层：把已确定的结构化事实润色成人话。
 *
 * 边界很硬——模型只允许改写表达，不允许新增事实：
 *   1. 提示词里只给证据与已定字段，明确禁止新增肥料、用量、时期
 *   2. 返回后必须过 FactValidator，校验不过直接丢弃、回退模板文本
 *   3. 任何异常（超时、限流、无密钥）都返回 empty，调用方无感
 *
 * 所以"接不接模型"对可用性没有影响，只影响文案读起来顺不顺。
 */
@Service
public class LlmPolisher {

    private static final Logger log = LoggerFactory.getLogger(LlmPolisher.class);

    private static final String SYSTEM = """
            你是农业技术推广领域的文字编辑。你的唯一任务是把给定的结构化要点改写成通顺、口语化的中文段落。

            硬性规则：
            1. 只能使用用户消息中给出的事实，禁止新增任何肥料名称、含量、用量、施用时期。
            2. 禁止给出诊断结论之外的推断，禁止评价"可能是别的原因"。
            3. 保留所有数值与单位，原样照抄，不得换算、不得四舍五入。
            4. 不使用 Markdown 标记，不列项目符号，输出连续段落，控制在 260 字以内。
            5. 语气平实，面向农户，不夸张、不承诺产量。
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;

    @Value("${agent.llm.enabled:false}")
    private boolean enabled;
    @Value("${agent.llm.timeout-ms:6000}")
    private long timeoutMs;

    public LlmPolisher(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    /**
     * @param facts 已确定的结构化要点（模板拼出的完整文本）
     * @return 润色后的文本；不可用时返回 empty
     */
    public Optional<String> polish(String facts) {
        if (!enabled || facts == null || facts.isBlank()) {
            return Optional.empty();
        }
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            log.debug("未配置 ChatModel，跳过润色");
            return Optional.empty();
        }
        try {
            String out = CompletableFuture
                    .supplyAsync(() -> ChatClient.create(model).prompt()
                            .system(SYSTEM)
                            .user("以下是已确定的事实要点，请改写为一段通顺的中文：\n" + facts)
                            .call()
                            .content())
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();
            if (out == null || out.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(out.trim());
        } catch (Exception ex) {
            log.warn("模型润色失败，回退模板文本：{}", ex.getClass().getSimpleName() + " " + ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 启动预热：首次调用要付 TLS 握手 + 服务端冷启动的代价，实测能吃掉 6 秒以上，
     * 直接导致演示第一句话退回模板。这里在应用就绪后异步打一发小请求把链路捂热，
     * 用户在页面上点第一次时就已经是热连接。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            return;
        }
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            long t0 = System.currentTimeMillis();
            try {
                CompletableFuture
                        .supplyAsync(() -> ChatClient.create(model).prompt()
                                .user("回复两个字：就绪")
                                .call()
                                .content())
                        .orTimeout(30, TimeUnit.SECONDS)
                        .join();
                log.info("模型预热完成，耗时 {} ms", System.currentTimeMillis() - t0);
            } catch (Exception ex) {
                log.warn("模型预热失败（不影响主流程，首次请求会自动回退模板）：{}", ex.getMessage());
            }
        });
    }

    public boolean enabled() {
        return enabled && chatModelProvider.getIfAvailable() != null;
    }
}
