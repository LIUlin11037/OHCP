package com.ohcp.cropagent.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用假向量验证 langchain4j 的内存向量存储接线是否正确。
 * 不需要 Embedding 接口、不需要网络——构建期产物本来就只是数组。
 */
@DisplayName("向量通道：InMemoryEmbeddingStore 检索")
class VectorIndexTest {

    @TempDir
    Path tmp;

    private Path writeVectors(String... lines) throws Exception {
        Path f = tmp.resolve("vectors.jsonl");
        Files.write(f, List.of(lines));
        return f;
    }

    @Test
    @DisplayName("查询向量与某条切片同向 -> 该条得分最高")
    void nearestHitWins() throws Exception {
        Path f = writeVectors(
                "{\"chunkId\":\"c-n\",\"element\":\"N\",\"text\":\"缺氮 老叶均匀黄化\",\"vector\":[1.0,0.0,0.0]}",
                "{\"chunkId\":\"c-fe\",\"element\":\"Fe\",\"text\":\"缺铁 新叶脉间黄化\",\"vector\":[0.0,1.0,0.0]}");

        VectorIndex idx = new VectorIndex();
        idx.loadFrom(f);

        assertTrue(idx.available());
        assertEquals(2, idx.size());

        List<VectorIndex.Hit> hits = idx.search(new double[]{0.9, 0.1, 0.0}, 2);
        assertFalse(hits.isEmpty());
        assertEquals("N", hits.get(0).element(), "应召回缺氮那条");
        assertTrue(hits.get(0).score() > hits.get(1).score());
    }

    @Test
    @DisplayName("文件缺失 -> 标记不可用，不抛异常")
    void missingFileDegrades() {
        VectorIndex idx = new VectorIndex();
        idx.loadFrom(tmp.resolve("not-exist.jsonl"));
        assertFalse(idx.available());
        assertTrue(idx.search(new double[]{1, 0, 0}, 3).isEmpty());
    }

    @Test
    @DisplayName("按元素取最高相似度")
    void bestPerElement() throws Exception {
        Path f = writeVectors(
                "{\"chunkId\":\"a1\",\"element\":\"N\",\"text\":\"t1\",\"vector\":[1.0,0.0]}",
                "{\"chunkId\":\"a2\",\"element\":\"N\",\"text\":\"t2\",\"vector\":[0.8,0.6]}");
        VectorIndex idx = new VectorIndex();
        idx.loadFrom(f);

        var best = idx.bestPerElement(idx.search(new double[]{1.0, 0.0}, 2));
        assertEquals(1, best.size());
        assertEquals(1.0, best.get("N"), 0.001);
    }
}
