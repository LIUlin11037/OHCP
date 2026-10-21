package com.ohcp.cropagent.retrieval;

import com.ohcp.cropagent.llm.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 混合检索：三路并行召回 + RRF 融合。
 *
 *   规则通道  —— 农学确定性，可解释性来源（缺氮命中"老叶+均匀黄化"）
 *   BM25      —— 术语精确（"脐腐病""花而不实"这类词面命中）
 *   向量通道  —— 语义泛化（"叶子发白发干"也能召回缺铁）
 *
 * 任何一路不可用都只是少一个视角，不阻断整体——capabilities 会如实告诉前端当前有哪几路。
 */
@Component
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final Bm25Index bm25;
    private final VectorIndex vectorIndex;
    private final EmbeddingClient embedding;

    @Value("${agent.retrieval.top-k:8}")
    private int topK;
    @Value("${agent.retrieval.rrf-k:60}")
    private int rrfK;
    @Value("${agent.retrieval.vector-enabled:true}")
    private boolean vectorEnabled;

    public HybridRetriever(Bm25Index bm25, VectorIndex vectorIndex, EmbeddingClient embedding) {
        this.bm25 = bm25;
        this.vectorIndex = vectorIndex;
        this.embedding = embedding;
    }

    public record Result(
            Map<String, Double> fused,
            Map<String, Double> semantic,
            List<String> capabilities
    ) {
        public double semanticOf(String element) {
            return semantic.getOrDefault(element, 0.0);
        }
    }

    public Result retrieve(String query, List<RuleMatcher.Score> ruleScores) {
        List<String> caps = new ArrayList<>();
        List<List<String>> ranked = new ArrayList<>();

        // 路 1：规则（已在 RuleMatcher 内按分数降序）
        List<String> ruleRank = new ArrayList<>();
        for (RuleMatcher.Score s : ruleScores) {
            if (s.ruleScore() > 0) {
                ruleRank.add(s.element());
            }
        }
        if (!ruleRank.isEmpty()) {
            ranked.add(ruleRank);
            caps.add("rule");
        }

        // 路 2：BM25
        List<String> bm25Rank = bm25.search(query, topK).stream().map(Bm25Index.Hit::element).toList();
        if (!bm25Rank.isEmpty()) {
            ranked.add(bm25Rank);
            caps.add("bm25");
        }

        // 路 3：向量（需要索引 + 查询期可向量化）
        Map<String, Double> semantic = new LinkedHashMap<>();
        if (vectorEnabled && vectorIndex.available()) {
            Optional<double[]> qv = embedding.embed(query);
            if (qv.isPresent()) {
                List<VectorIndex.Hit> vhits = vectorIndex.search(qv.get(), topK);
                List<String> vRank = vhits.stream().map(VectorIndex.Hit::element).distinct().toList();
                if (!vRank.isEmpty()) {
                    ranked.add(vRank);
                    semantic.putAll(vectorIndex.bestPerElement(vhits));
                    caps.add("vector");
                }
            } else if (!embedding.configured()) {
                log.debug("查询期向量化未配置，向量通道跳过");
            }
        }

        caps.add("template");
        return new Result(RrfFuser.fuse(rrfK, ranked), semantic, caps);
    }
}
