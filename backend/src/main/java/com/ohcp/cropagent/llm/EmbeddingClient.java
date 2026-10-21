package com.ohcp.cropagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询期向量化。只在"用户输入需要转向量"时调用，构建期的向量早已落盘。
 *
 * 设计要点：拿不到密钥、网络不通、响应异常，一律返回 empty——调用方直接降级到
 * 规则 + BM25，绝不让整条诊断链路因为向量服务挂掉而失败。
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    @Value("${agent.embedding.enabled:true}")
    private boolean enabled;
    @Value("${agent.embedding.base-url:}")
    private String baseUrl;
    @Value("${agent.embedding.api-key:}")
    private String apiKey;
    @Value("${agent.embedding.model:text-embedding-v3}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();
    private volatile boolean warned = false;

    private RestClient client;

    public Optional<double[]> embed(String text) {
        if (!enabled || baseUrl.isBlank() || apiKey.isBlank() || text == null || text.isBlank()) {
            return Optional.empty();
        }
        double[] cached = cache.get(text);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            if (client == null) {
                client = RestClient.builder().baseUrl(baseUrl).build();
            }
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", List.of(text),
                    "encoding_format", "float"
            ));
            String resp = client.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode emb = mapper.readTree(resp).path("data").path(0).path("embedding");
            if (!emb.isArray()) {
                return Optional.empty();
            }
            double[] v = new double[emb.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = emb.get(i).asDouble();
            }
            if (cache.size() < 500) {
                cache.put(text, v);
            }
            return Optional.of(v);
        } catch (Exception ex) {
            if (!warned) {
                log.warn("查询期向量化不可用，降级为规则 + BM25：{}", ex.getMessage());
                warned = true;
            }
            return Optional.empty();
        }
    }

    public boolean configured() {
        return enabled && !baseUrl.isBlank() && !apiKey.isBlank();
    }
}
