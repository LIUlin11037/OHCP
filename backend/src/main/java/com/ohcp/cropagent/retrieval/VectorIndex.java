package com.ohcp.cropagent.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量通道：语义召回。
 *
 * 向量存储与相似度检索直接用 langchain4j 的 InMemoryEmbeddingStore——
 * 这是本项目里 langchain4j 的实际落点（选型表第 2 项「智能体框架」）。
 * 只借它的存储与检索组件，模型层仍走 Spring AI，不引它的 Spring Boot Starter，
 * 避免两套框架抢同一个 ChatModel Bean。
 *
 * 向量文件由 crawler/embed.py 在构建期生成，运行期只加载不计算，
 * 所以断网也能语义检索；文件缺失时整体不可用，调用方自动降级。
 */
@Component
public class VectorIndex {

    private static final Logger log = LoggerFactory.getLogger(VectorIndex.class);

    private static final String META_ELEMENT = "element";
    private static final String META_CHUNK_ID = "chunkId";

    @Value("${agent.vectors.file}")
    private String vectorsFile;

    private final ObjectMapper mapper = new ObjectMapper();
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
    private int chunkCount = 0;

    public record Hit(String element, String chunkId, String text, double score) {
    }

    @PostConstruct
    public void load() {
        loadFrom(Path.of(vectorsFile));
    }

    /** 抽出来便于单测直接喂一个临时文件，不依赖 Spring 上下文。 */
    void loadFrom(Path p) {
        if (!Files.exists(p)) {
            log.warn("向量文件不存在，语义检索关闭：{}", p.toAbsolutePath());
            return;
        }
        try {
            for (String line : Files.readAllLines(p)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode n = mapper.readTree(line);
                JsonNode vs = n.path("vector");
                if (!vs.isArray() || vs.isEmpty()) {
                    continue;
                }
                float[] v = new float[vs.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = (float) vs.get(i).asDouble();
                }
                String element = n.path("element").asText();
                String chunkId = n.path("chunkId").asText();

                TextSegment segment = TextSegment.from(
                        n.path("text").asText(),
                        Metadata.from(Map.of(META_ELEMENT, element, META_CHUNK_ID, chunkId)));
                store.add(chunkId, Embedding.from(v), segment);
                chunkCount++;
            }
            log.info("向量索引加载完成：{} 条切片（InMemoryEmbeddingStore）", chunkCount);
        } catch (Exception ex) {
            log.error("向量索引加载失败，语义检索关闭：{}", ex.getMessage());
            chunkCount = 0;
        }
    }

    public boolean available() {
        return chunkCount > 0;
    }

    public int size() {
        return chunkCount;
    }

    public List<Hit> search(double[] queryVec, int topK) {
        if (!available() || queryVec == null) {
            return List.of();
        }
        float[] q = new float[queryVec.length];
        for (int i = 0; i < q.length; i++) {
            q[i] = (float) queryVec[i];
        }
        try {
            var result = store.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(Embedding.from(q))
                    .maxResults(topK)
                    .minScore(0.0)
                    .build());

            List<Hit> hits = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> m : result.matches()) {
                TextSegment seg = m.embedded();
                hits.add(new Hit(
                        seg.metadata().getString(META_ELEMENT),
                        seg.metadata().getString(META_CHUNK_ID),
                        seg.text(),
                        m.score()));
            }
            return hits;
        } catch (Exception ex) {
            log.warn("向量检索异常，本路跳过：{}", ex.getMessage());
            return List.of();
        }
    }

    /** 每个元素取最高相似度，供置信度的"语义因子"使用。 */
    public Map<String, Double> bestPerElement(List<Hit> hits) {
        Map<String, Double> m = new HashMap<>();
        for (Hit h : hits) {
            m.merge(h.element(), h.score(), Math::max);
        }
        return m;
    }
}
