package com.ohcp.cropagent.guardrail;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实校验：输出端闸门。
 *
 * 思路很朴素——凡是出现在回答里的数值（用量、浓度、比例），
 * 必须能在知识条目里找到一模一样的一串。找不到就说明这句话不是知识库说的，
 * 要么是模型编的，要么是模板拼错了，两种情况都不该放给用户看。
 *
 * 这就是"禁止完全依靠大模型直接生成结果"的工程化落地：
 * 不是靠提示词里写"不要编造"，而是靠代码把编造的内容拦下来。
 */
@Component
public class FactValidator {

    /** 数值 + 单位：5~8 kg/亩、0.2%~0.5%、1000 至 1500 千克每亩 等常见写法。 */
    private static final Pattern QUANTITY = Pattern.compile(
            "(\\d+(?:\\.\\d+)?(?:\\s*[~\\-至]\\s*\\d+(?:\\.\\d+)?)?)\\s*(kg|公斤|千克|g|克|%|毫升|ml)");

    public record Result(boolean passed, List<String> checked, List<String> unsupported) {
        public static Result empty() {
            return new Result(true, List.of(), List.of());
        }
    }

    /**
     * @param output   待校验文本（模板拼接或模型生成）
     * @param evidence 允许的事实源（知识条目全部文本）
     */
    public Result validate(String output, String evidence) {
        Set<String> checked = new LinkedHashSet<>();
        List<String> unsupported = new ArrayList<>();

        if (output == null || output.isBlank()) {
            return Result.empty();
        }
        String src = normalize(evidence == null ? "" : evidence);

        Matcher m = QUANTITY.matcher(output);
        while (m.find()) {
            String token = m.group(1) + " " + m.group(2);
            checked.add(token.trim());
            // 只比对数值部分，单位写法差异（kg/公斤）不算错
            String num = m.group(1).replaceAll("\\s*[~\\-至]\\s*", "~");
            if (!src.contains(num)) {
                unsupported.add(token.trim());
            }
        }

        return new Result(unsupported.isEmpty(), List.copyOf(checked), List.copyOf(unsupported));
    }

    private String normalize(String s) {
        return s.replace("至", "~")
                .replace("-", "~")
                .replace("到", "~");
    }
}
