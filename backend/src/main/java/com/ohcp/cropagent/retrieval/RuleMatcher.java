package com.ohcp.cropagent.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohcp.cropagent.knowledge.KnowledgeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则通道：症状特征矩阵打分。
 *
 * 这是"核心识别逻辑依赖本地知识库"最直接的落点——所有权重都在
 * data/kb/symptom_rules.json 里，改权重不用改代码。
 *
 * 打分公式：命中正权重之和 / 该元素正权重总和 + 命中负权重之和，截断到 [0,1]。
 * 负权重是关键：缺氮却命中"新叶首发"会被狠扣，这是区分缺氮与缺硫的唯一手段。
 */
@Component
public class RuleMatcher {

    private final KnowledgeRepository kb;

    public RuleMatcher(KnowledgeRepository kb) {
        this.kb = kb;
    }

    public record Score(String element, String elementName, double ruleScore, List<String> matched) {
    }

    public List<Score> scoreAll(Map<String, String> hits) {
        List<Score> out = new ArrayList<>();
        JsonNode rules = kb.rules();
        if (rules == null || rules.path("elements").isMissingNode()) {
            return out;
        }

        JsonNode featuresMeta = rules.path("features");
        Iterator<Map.Entry<String, JsonNode>> it = rules.path("elements").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String code = e.getKey();
            JsonNode def = e.getValue();

            double pos = 0;
            double total = 0;
            List<String> matched = new ArrayList<>();

            Iterator<Map.Entry<String, JsonNode>> fit = def.path("features").fields();
            while (fit.hasNext()) {
                Map.Entry<String, JsonNode> f = fit.next();
                double w = f.getValue().asDouble();
                total += w;
                if (hits.containsKey(f.getKey())) {
                    pos += w;
                    matched.add(label(featuresMeta, f.getKey()));
                }
            }

            double neg = 0;
            Iterator<Map.Entry<String, JsonNode>> nit = def.path("negative").fields();
            while (nit.hasNext()) {
                Map.Entry<String, JsonNode> f = nit.next();
                if (hits.containsKey(f.getKey())) {
                    neg += f.getValue().asDouble();
                }
            }

            double raw = (total <= 0 ? 0 : pos / total) + neg;
            double score = Math.max(0, Math.min(1, raw));
            out.add(new Score(code, kb.elementName(code), score, matched));
        }
        out.sort((a, b) -> Double.compare(b.ruleScore(), a.ruleScore()));
        return out;
    }

    private String label(JsonNode featuresMeta, String code) {
        JsonNode f = featuresMeta.path(code);
        String label = f.path("label").asText(code);
        return label;
    }

    /** 供调试：把某元素的所有权重摊开，答辩时可以直接展示。 */
    public Map<String, Object> explain(String element) {
        Map<String, Object> m = new LinkedHashMap<>();
        JsonNode def = kb.rules() == null ? null : kb.rules().path("elements").path(element);
        if (def == null || def.isMissingNode()) {
            return m;
        }
        Map<String, Double> fw = new HashMap<>();
        def.path("features").fields().forEachRemaining(x -> fw.put(x.getKey(), x.getValue().asDouble()));
        m.put("features", fw);
        m.put("negative", def.path("negative"));
        return m;
    }
}
