package com.ohcp.cropagent.analytics;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 极简查询埋点：记录"什么作物 + 什么症状描述"被问了多少次，供看板接口使用。
 *
 * 内存统计、重启即清零——课堂演示不需要持久化，但要有一个能看的数字。
 */
@Service
public class AnalyticsService {

    private final Map<String, AtomicInteger> elementHits = new ConcurrentHashMap<>();
    private final Map<String, Integer> cropHits = new ConcurrentHashMap<>();
    private final List<String> recentSymptoms = new ArrayList<>();

    public void record(String crop, String symptomText, String topElement) {
        if (topElement != null) {
            elementHits.computeIfAbsent(topElement, k -> new AtomicInteger()).incrementAndGet();
        }
        if (crop != null && !crop.isBlank()) {
            cropHits.merge(crop, 1, Integer::sum);
        }
        if (symptomText != null && !symptomText.isBlank()) {
            synchronized (recentSymptoms) {
                recentSymptoms.add(0, symptomText.length() > 40 ? symptomText.substring(0, 40) : symptomText);
                while (recentSymptoms.size() > 20) {
                    recentSymptoms.remove(recentSymptoms.size() - 1);
                }
            }
        }
    }

    public record Row(String crop, String symptom, Integer count) {
    }

    /** 按作物聚合，症状取最近一次的高频描述占位，够看板展示即可。 */
    public List<Row> hot() {
        List<Row> rows = new ArrayList<>();
        cropHits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .forEach(e -> rows.add(new Row(e.getKey(), null, e.getValue())));
        return rows;
    }

    public List<String> recentSymptoms() {
        synchronized (recentSymptoms) {
            return List.copyOf(recentSymptoms);
        }
    }

    public int elementCount(String element) {
        AtomicInteger c = elementHits.get(element);
        return c == null ? 0 : c.get();
    }
}
