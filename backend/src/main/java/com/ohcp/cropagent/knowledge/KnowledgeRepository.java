package com.ohcp.cropagent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识层：运行期只读。启动时一次性把知识、规则、思政模板加载进内存。
 * 请求链路不做任何写操作，保证并发安全与结果可复现。
 */
@Component
public class KnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRepository.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${agent.kb.dir}")
    private String kbDir;

    /** element -> 知识条目 */
    private final Map<String, Map<String, Object>> byElement = new LinkedHashMap<>();
    private JsonNode rules;
    private JsonNode sizheng;

    @PostConstruct
    public void load() {
        Path dir = Path.of(kbDir).toAbsolutePath().normalize();
        try {
            Path kb = dir.resolve("deficiency_kb.jsonl");
            if (Files.exists(kb)) {
                for (String line : Files.readAllLines(kb)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> e = mapper.readValue(line, Map.class);
                    byElement.put((String) e.get("element"), e);
                }
            }
            rules = readJson(dir.resolve("symptom_rules.json"));
            sizheng = readJson(dir.resolve("sizheng.json"));
            log.info("知识库加载完成：{} 条条目，规则={}，思政={}",
                    byElement.size(), rules != null, sizheng != null);
        } catch (Exception ex) {
            log.error("知识库加载失败：{}", ex.getMessage());
            throw new IllegalStateException("知识库加载失败，请检查 " + dir, ex);
        }
    }

    private JsonNode readJson(Path p) throws Exception {
        if (!Files.exists(p)) {
            return null;
        }
        return mapper.readTree(p.toFile());
    }

    public JsonNode rules() {
        return rules;
    }

    public JsonNode sizheng() {
        return sizheng;
    }

    public int size() {
        return byElement.size();
    }

    public Optional<Map<String, Object>> adviceOf(String element) {
        return Optional.ofNullable(byElement.get(element));
    }

    /** 元素代码大小写不敏感查找（前端可能传 Fe / FE / fe）。 */
    public Optional<Map<String, Object>> adviceOfIgnoreCase(String element) {
        if (element == null) {
            return Optional.empty();
        }
        Map<String, Object> hit = byElement.get(element);
        if (hit != null) {
            return Optional.of(hit);
        }
        for (Map.Entry<String, Map<String, Object>> e : byElement.entrySet()) {
            if (e.getKey().equalsIgnoreCase(element)) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    /** 知识条目转成检索用的证据文本，用于前端展示"凭什么这么判"。 */
    public String evidenceText(String element) {
        Map<String, Object> e = byElement.get(element);
        if (e == null) {
            return "";
        }
        String sym = String.join("、", toList(e.get("colorPattern"))) + "；" + String.join("、", toList(e.get("morphology")));
        return e.get("elementName") + " 缺素症：症状为" + sym + "。" + e.get("intro");
    }

    public String sourceUrl(String element) {
        Map<String, Object> e = byElement.get(element);
        return e == null ? null : (String) e.get("sourceUrl");
    }

    public String differential(String element) {
        Map<String, Object> e = byElement.get(element);
        if (e == null) {
            return null;
        }
        List<String> d = toList(e.get("differential"));
        return d.isEmpty() ? null : String.join("；", d);
    }

    public List<String> elements() {
        return new ArrayList<>(byElement.keySet());
    }

    /** 中文元素名从知识条目取（规则矩阵里不含中文名，避免两处维护）。 */
    public String elementName(String code) {
        Map<String, Object> e = byElement.get(code);
        if (e == null) {
            return code;
        }
        Object n = e.get("elementName");
        return n == null ? code : String.valueOf(n);
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object o) {
        return o instanceof List ? (List<String>) o : List.of();
    }
}
