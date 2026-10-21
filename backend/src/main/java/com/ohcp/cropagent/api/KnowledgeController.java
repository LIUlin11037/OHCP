package com.ohcp.cropagent.api;

import com.ohcp.cropagent.knowledge.KnowledgeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识条目查询。前端做「科普小卡片」时用这个，不要指望从 diagnose 响应里抠字段。
 *
 * 返回的就是 knowledge_kb.jsonl 里的那条记录（已整理成前端友好的字段顺序），
 * 与向量数据无关——向量只参与检索，不参与展示。
 */
@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {

    private final KnowledgeRepository kb;

    public KnowledgeController(KnowledgeRepository kb) {
        this.kb = kb;
    }

    @GetMapping("/knowledge/{element}")
    public ApiResponse<Map<String, Object>> knowledge(@PathVariable String element) {
        Map<String, Object> entry = kb.adviceOfIgnoreCase(element).orElse(null);
        if (entry == null) {
            return ApiResponse.fail(2002, "未收录该元素的缺素知识：" + element);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("element", entry.get("element"));
        data.put("elementName", entry.get("elementName"));
        data.put("crops", entry.get("crops"));
        data.put("leafAge", entry.get("leafAge"));
        data.put("colorPattern", entry.get("colorPattern"));
        data.put("morphology", entry.get("morphology"));
        data.put("intro", entry.get("intro"));
        data.put("differential", entry.get("differential"));
        data.put("sourceUrl", entry.get("sourceUrl"));
        data.put("publisher", entry.get("publisher"));
        return ApiResponse.ok(data);
    }
}
