package com.ohcp.cropagent.retrieval;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF 倒数排名融合。
 *
 * 为什么不用加权求和：三路分数量纲完全不同——规则是 0~1 的匹配度，
 * BM25 是无界值（可能 3 也可能 30），余弦是 -1~1。加权求和要反复调参，换个数据集就失准。
 * RRF 只看排名不看分值，只多一个参数 k，天然鲁棒。
 */
public final class RrfFuser {

    private RrfFuser() {
    }

    /**
     * @param k      平滑常数，常用 60；越大越平均，越小越偏向头部
     * @param ranked 每路一个"按相关性降序排好的元素列表"
     */
    public static Map<String, Double> fuse(int k, List<List<String>> ranked) {
        Map<String, Double> fused = new HashMap<>();
        for (List<String> list : ranked) {
            for (int i = 0; i < list.size(); i++) {
                fused.merge(list.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        // 按融合分降序返回
        return fused.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }
}
