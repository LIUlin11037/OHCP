package com.ohcp.cropagent.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RRF 融合：只看排名，不看分数量纲")
class RrfFuserTest {

    @Test
    @DisplayName("两路都排第一的元素胜出")
    void topInBothWins() {
        Map<String, Double> fused = RrfFuser.fuse(60, List.of(
                List.of("N", "Mg", "P"),
                List.of("N", "K", "Mg")
        ));
        assertEquals("N", fused.keySet().iterator().next());
        assertTrue(fused.get("N") > fused.get("Mg"));
    }

    @Test
    @DisplayName("一路没召回的元素只是分数低，不会丢")
    void missingInOneChannelStillScored() {
        Map<String, Double> fused = RrfFuser.fuse(60, List.of(
                List.of("N", "Mg"),
                List.of("K")
        ));
        assertEquals(3, fused.size());
        assertTrue(fused.containsKey("K"));
    }

    @Test
    @DisplayName("k 越小越偏向头部")
    void smallKSharpen() {
        Map<String, Double> small = RrfFuser.fuse(1, List.of(List.of("N", "Mg")));
        Map<String, Double> large = RrfFuser.fuse(1000, List.of(List.of("N", "Mg")));
        assertTrue(small.get("N") / small.get("Mg") > large.get("N") / large.get("Mg"));
    }
}
