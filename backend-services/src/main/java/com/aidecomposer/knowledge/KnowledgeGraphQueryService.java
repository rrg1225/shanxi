package com.aidecomposer.knowledge;

import com.aidecomposer.knowledge.dto.KnowledgeGraphEdgeResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeGraphQueryService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphQueryService.class);

    private final KnowledgeGraphEdgeMapper edgeMapper;

    public KnowledgeGraphQueryService(KnowledgeGraphEdgeMapper edgeMapper) {
        this.edgeMapper = edgeMapper;
    }

    /**
     * 查询得分 ≥ minScore 的边，再按「每个 chunk 节点最多保留 maxEdgesPerNode 条关联边」做全局贪心裁剪：
     * 按 similarity_score 降序遍历，仅当边的两端当前度数都未达上限时才收录。
     */
    public List<KnowledgeGraphEdgeResponse> listEdgesFiltered(double minScore, int maxEdgesPerNode) {
        if (maxEdgesPerNode < 1) {
            maxEdgesPerNode = 1;
        }
        try {
            List<KnowledgeGraphEdge> rows = edgeMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeGraphEdge>()
                            .ge(KnowledgeGraphEdge::getSimilarityScore, minScore)
                            .orderByDesc(KnowledgeGraphEdge::getSimilarityScore)
            );
            rows.sort(Comparator.comparing(KnowledgeGraphEdge::getSimilarityScore).reversed());

            Map<Long, Integer> degree = new HashMap<>();
            List<KnowledgeGraphEdgeResponse> out = new ArrayList<>();
            for (KnowledgeGraphEdge e : rows) {
                long a = e.getSourceChunkId();
                long b = e.getTargetChunkId();
                int da = degree.getOrDefault(a, 0);
                int db = degree.getOrDefault(b, 0);
                if (da < maxEdgesPerNode && db < maxEdgesPerNode) {
                    degree.put(a, da + 1);
                    degree.put(b, db + 1);
                    KnowledgeGraphEdgeResponse dto = new KnowledgeGraphEdgeResponse();
                    dto.setSourceChunkId(a);
                    dto.setTargetChunkId(b);
                    dto.setSimilarityScore(e.getSimilarityScore());
                    out.add(dto);
                }
            }
            return out;
        } catch (Exception ex) {
            // 本地未建表、库不可达、Milvus 无关但表缺失等：避免 500，前端可走邻近连线兜底
            log.warn("knowledge-graph edges query failed, returning empty list: {}", ex.toString());
            return List.of();
        }
    }
}
