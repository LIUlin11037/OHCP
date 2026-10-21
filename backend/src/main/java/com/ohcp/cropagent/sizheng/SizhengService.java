package com.ohcp.cropagent.sizheng;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohcp.cropagent.knowledge.KnowledgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 思政注入：按元素选一条人物故事 + 一条价值引导。
 *
 * 与模型完全无关——即使模型不可用、走纯模板降级，思政内容照样输出，
 * 不会出现"演示到思政环节掉链子"。这是把它做成模板库而不是提示词的原因。
 */
@Service
public class SizhengService {

    private final KnowledgeRepository kb;

    public SizhengService(KnowledgeRepository kb) {
        this.kb = kb;
    }

    public Map<String, String> pick(String element) {
        JsonNode root = kb.sizheng();
        if (root == null) {
            return Map.of("story", "", "value", "");
        }
        return Map.of(
                "story", pickStory(root.path("story"), element),
                "value", pickByIndex(root.path("value"), element)
        );
    }

    private String pickStory(JsonNode arr, String element) {
        String fallback = "";
        for (JsonNode n : arr) {
            String el = n.path("element").isNull() ? null : n.path("element").asText(null);
            if (element != null && element.equals(el)) {
                return n.path("text").asText("");
            }
            if (el == null) {
                fallback = n.path("text").asText("");
            }
        }
        return fallback;
    }

    /** 按元素做确定性轮转：不同元素文案不同，同一元素每次演示文案稳定。 */
    private String pickByIndex(JsonNode arr, String element) {
        List<JsonNode> list = new java.util.ArrayList<>();
        arr.forEach(list::add);
        if (list.isEmpty()) {
            return "";
        }
        int idx = Math.floorMod(String.valueOf(element).hashCode(), list.size());
        return list.get(idx).path("text").asText("");
    }
}
