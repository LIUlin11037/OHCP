package com.ohcp.cropagent.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示前自检。10 秒内告出「知识库有没有加载、向量有没有就绪」，
 * 比演示到一半白屏强得多。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Value("${agent.kb.dir}")
    private String kbDir;

    @Value("${agent.vectors.file}")
    private String vectorsFile;

    @Value("${agent.kb.hot-reload:true}")
    private boolean hotReload;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("knowledgeBase", knowledgeBase());
        components.put("vectorIndex", vectorIndex());
        components.put("llm", Map.of("status", "SKIPPED", "reason", "模型通道尚未接入"));
        components.put("profile", System.getProperty("spring.profiles.active", "default"));

        Map<String, Object> data = new LinkedHashMap<>();
        boolean up = "UP".equals(((Map<?, ?>) components.get("knowledgeBase")).get("status"));
        data.put("status", up ? "UP" : "DOWN");
        data.put("components", components);
        return ApiResponse.ok(data);
    }

    private Map<String, Object> knowledgeBase() {
        Map<String, Object> m = new LinkedHashMap<>();
        Path dir = Path.of(kbDir);
        if (!Files.isDirectory(dir)) {
            m.put("status", "DOWN");
            m.put("reason", "目录不存在：" + dir.toAbsolutePath());
            return m;
        }
        Path kb = dir.resolve("deficiency_kb.jsonl");
        long items = countLines(kb);
        Path rules = dir.resolve("symptom_rules.json");
        Path sizheng = dir.resolve("sizheng.json");

        m.put("status", items > 0 ? "UP" : "DOWN");
        m.put("items", items);
        m.put("rulesLoaded", Files.exists(rules));
        m.put("sizhengLoaded", Files.exists(sizheng));
        m.put("hotReload", hotReload);
        m.put("dir", dir.toAbsolutePath().toString());
        return m;
    }

    private Map<String, Object> vectorIndex() {
        Map<String, Object> m = new LinkedHashMap<>();
        Path f = Path.of(vectorsFile);
        if (!Files.exists(f)) {
            m.put("status", "DOWN");
            m.put("reason", "向量文件缺失，语义检索将降级为 BM25");
            return m;
        }
        m.put("status", "UP");
        m.put("chunks", countLines(f));
        return m;
    }

    private long countLines(Path p) {
        if (!Files.exists(p)) {
            return 0;
        }
        try (var lines = Files.lines(p)) {
            return lines.filter(s -> !s.isBlank()).count();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 便于本地快速确认哪几个接口已经就绪。 */
    @GetMapping("/routes")
    public ApiResponse<List<String>> routes() {
        return ApiResponse.ok(List.of(
                "GET  /api/v1/health",
                "GET  /api/v1/routes",
                "POST /api/v1/diagnose      (待实现)",
                "POST /api/v1/advice        (待实现)",
                "POST /api/v1/chat          (待实现)"
        ));
    }
}
