package com.ohcp.cropagent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.ohcp.cropagent.knowledge.KnowledgeRepository;
import com.ohcp.cropagent.retrieval.RuleMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 置信度计算：四因子加权。
 *
 *   规则匹配度 0.45 —— 命中了多少症状特征
 *   语义相似度 0.30 —— 向量通道；不可用时权重动态让给其余三项
 *   部位先验   0.15 —— 元素移动性是否与"老叶/新叶"一致（农学判别关键）
 *   作物先验   0.10 —— 该作物是否常缺该元素
 *
 * 最后做一次 pow(conf, sharpen) 压缩中间区：0.9 以上基本不动，
 * 0.5 附近被压到 0.35 左右——避免给出"半信半疑"的结论。
 */
@Component
public class ConfidenceScorer {

    private final KnowledgeRepository kb;

    @Value("${agent.confidence.weight.rule:0.45}")
    private double wRule;
    @Value("${agent.confidence.weight.semantic:0.30}")
    private double wSemantic;
    @Value("${agent.confidence.weight.part:0.15}")
    private double wPart;
    @Value("${agent.confidence.weight.crop:0.10}")
    private double wCrop;
    @Value("${agent.confidence.sharpen:1.6}")
    private double sharpen;

    public ConfidenceScorer(KnowledgeRepository kb) {
        this.kb = kb;
    }

    /**
     * @param semantic 向量通道得分，不可用时传 null，权重自动让给其余因子
     */
    public double score(RuleMatcher.Score s, Map<String, String> hits, Double semantic) {
        double weighted = 0;
        double totalW = 0;

        weighted += wRule * s.ruleScore();
        totalW += wRule;

        if (semantic != null) {
            weighted += wSemantic * semantic;
            totalW += wSemantic;
        }

        weighted += wPart * partPrior(s.element(), hits);
        totalW += wPart;

        weighted += wCrop * cropPrior(s.element());
        totalW += wCrop;

        double conf = totalW == 0 ? 0 : weighted / totalW;
        return Math.max(0, Math.min(1, Math.pow(conf, sharpen)));
    }

    /**
     * 部位先验：可移动元素（N/P/K/Mg）缺素先显老叶；不可移动（Fe/Ca/B）先显新叶。
     * 这一条能把缺氮与缺硫、缺镁与缺铁这类易混案例直接分开，且完全可解释。
     */
    public double partPrior(String element, Map<String, String> hits) {
        String mobility = mobilityOf(element);
        boolean oldHit = hits.containsKey("old_leaf");
        boolean newHit = hits.containsKey("new_leaf");

        if (!oldHit && !newHit) {
            return 0.5;   // 没说部位，保持中性
        }
        return switch (mobility) {
            case "mobile" -> oldHit ? 1.0 : 0.2;
            case "immobile" -> newHit ? 1.0 : 0.2;
            default -> 0.85;   // semi：两边都说得通
        };
    }

    private String mobilityOf(String element) {
        JsonNode m = kb.rules() == null ? null : kb.rules().path("mobility");
        if (m == null || m.path(element).isMissingNode()) {
            return "semi";
        }
        return m.path(element).asText("semi");
    }

    private double cropPrior(String element) {
        JsonNode p = kb.rules() == null ? null : kb.rules().path("elements").path(element).path("prior");
        return p == null || p.isMissingNode() ? 0.5 : p.asDouble();
    }

    public String level(double conf) {
        if (conf >= 0.65) {
            return "HIGH";
        }
        return conf >= 0.40 ? "MEDIUM" : "LOW";
    }
}
