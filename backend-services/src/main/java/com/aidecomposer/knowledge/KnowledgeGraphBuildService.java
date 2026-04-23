package com.aidecomposer.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aidecomposer.rag.RagDocumentChunk;
import com.aidecomposer.rag.RagDocumentChunkMapper;
import com.aidecomposer.util.SnowflakeIdWorker;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Milvus 中已入库的 chunk embedding，自动生成知识图谱边。
 * <p>
 * 为什么不直接 O(n²) 两两算余弦？
 * <ul>
 *   <li>n 上万时 n² 不可接受；Milvus 已按 COSINE 建 ANN 索引，用 search(KNN) 等价于「对每个向量找 TopK 近邻」。</li>
 *   <li>ANN 返回的 score 与索引 metric 一致（本项目为 COSINE），分数越大表示越相似，可直接作为 similarity_score 落库。</li>
 * </ul>
 * 批处理策略：
 * <ul>
 *   <li>从 MySQL 按主键游标分页拉 chunkId（每批 chunkIdBatchSize）。</li>
 *   <li>对一批 id 发 Milvus query 拉取向量（一次 RPC，避免 n 次 query）。</li>
 *   <li>对批内每个向量做一次 search(topK)，写入超过 buildMinScore 的边（排除自环）。</li>
 * </ul>
 */
@Service
public class KnowledgeGraphBuildService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphBuildService.class);

    private static final int MILVUS_EXPR_IN_CHUNK = 50;
    private static final int INSERT_BATCH = 400;

    private final KnowledgeGraphMilvusBridge milvusBridge;
    private final MilvusGraphProperties milvusProps;
    private final KnowledgeGraphBuildProperties buildProps;
    private final RagDocumentChunkMapper chunkMapper;
    private final KnowledgeGraphEdgeMapper edgeMapper;
    private final SnowflakeIdWorker snowflakeIdWorker;

    public KnowledgeGraphBuildService(KnowledgeGraphMilvusBridge milvusBridge,
                                      MilvusGraphProperties milvusProps,
                                      KnowledgeGraphBuildProperties buildProps,
                                      RagDocumentChunkMapper chunkMapper,
                                      KnowledgeGraphEdgeMapper edgeMapper,
                                      @Value("${app.snowflake.worker-id}") long workerId,
                                      @Value("${app.snowflake.datacenter-id}") long datacenterId) {
        this.milvusBridge = milvusBridge;
        this.milvusProps = milvusProps;
        this.buildProps = buildProps;
        this.chunkMapper = chunkMapper;
        this.edgeMapper = edgeMapper;
        this.snowflakeIdWorker = new SnowflakeIdWorker(workerId, datacenterId, 1577836800000L);
    }

    /**
     * 全量重建（定时任务调用）：可选先 truncate 边表，再扫描全部 chunk。
     */
    public void rebuildAllEdges() {
        if (!buildProps.isEnabled()) {
            log.info("Knowledge graph build skipped: app.knowledge-graph.enabled=false");
            return;
        }
        String collection = milvusProps.getGraphCollectionName();
        if (collection == null || collection.isBlank()) {
            log.warn("milvus.graph-collection-name empty, skip knowledge graph build");
            return;
        }

        MilvusServiceClient client = milvusBridge.client();
        MetricType metricType = parseMetric(milvusProps.getMetricType());

        if (buildProps.isRebuildTruncateEdges()) {
            edgeMapper.delete(new LambdaQueryWrapper<>());
            log.info("knowledge_graph_edge truncated before rebuild");
        }
        // 无向边已规范化 (min,max)；双向 ANN 仍可能对同一对 chunk 各命中一次，落库前去重
        Set<String> seenPairs = new HashSet<>();

        double minScore = buildProps.getBuildMinScore();
        int topK = Math.max(2, buildProps.getMilvusSearchTopK());
        int batchSize = Math.max(10, buildProps.getChunkIdBatchSize());

        long lastId = 0L;
        int totalChunks = 0;
        int totalEdges = 0;
        List<KnowledgeGraphEdge> insertBuffer = new ArrayList<>();

        while (true) {
            List<RagDocumentChunk> batch = chunkMapper.selectList(
                    new LambdaQueryWrapper<RagDocumentChunk>()
                            .select(RagDocumentChunk::getId)
                            .isNotNull(RagDocumentChunk::getMilvusEntityId)
                            .gt(RagDocumentChunk::getId, lastId)
                            .orderByAsc(RagDocumentChunk::getId)
                            .last("LIMIT " + batchSize)
            );
            if (batch.isEmpty()) {
                break;
            }
            lastId = batch.get(batch.size() - 1).getId();
            List<Long> chunkIds = batch.stream().map(RagDocumentChunk::getId).collect(Collectors.toList());

            // Milvus expr 过长会失败，再按子列表切分 query
            for (int from = 0; from < chunkIds.size(); from += MILVUS_EXPR_IN_CHUNK) {
                List<Long> sub = chunkIds.subList(from, Math.min(from + MILVUS_EXPR_IN_CHUNK, chunkIds.size()));
                Map<Long, List<Float>> vectors = queryEmbeddingsByIds(client, collection, sub);
                for (Long chunkId : sub) {
                    List<Float> vector = vectors.get(chunkId);
                    if (vector == null || vector.isEmpty()) {
                        continue;
                    }
                    totalChunks++;
                    int added = searchAndCollectEdges(
                            client,
                            collection,
                            metricType,
                            chunkId,
                            vector,
                            topK,
                            minScore,
                            insertBuffer,
                            seenPairs
                    );
                    totalEdges += added;
                    flushIfNeeded(insertBuffer, false);
                }
            }
            log.info("Knowledge graph build progress: lastChunkId={}, chunksProcessed={}, edgesBuffered={}",
                    lastId, totalChunks, totalEdges);
        }
        flushIfNeeded(insertBuffer, true);
        log.info("Knowledge graph build finished: chunksWithVector={}, edgesInserted={}", totalChunks, totalEdges);
    }

    private void flushIfNeeded(List<KnowledgeGraphEdge> buffer, boolean force) {
        if (buffer.isEmpty()) {
            return;
        }
        if (!force && buffer.size() < INSERT_BATCH) {
            return;
        }
        for (KnowledgeGraphEdge e : buffer) {
            edgeMapper.insert(e);
        }
        buffer.clear();
    }

    private MetricType parseMetric(String raw) {
        if (raw == null) {
            return MetricType.COSINE;
        }
        try {
            return MetricType.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            return MetricType.COSINE;
        }
    }

    /**
     * 按主键批量 query，返回 chunkId -> 向量。
     */
    private Map<Long, List<Float>> queryEmbeddingsByIds(MilvusServiceClient client,
                                                         String collection,
                                                         List<Long> ids) {
        String inList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        QueryParam param = QueryParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("id in [" + inList + "]")
                .withOutFields(List.of("id", "embedding"))
                .withConsistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .build();

        R<QueryResults> resp = client.query(param);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            log.warn("Milvus query failed: {}", resp.getMessage());
            return Collections.emptyMap();
        }
        Map<Long, List<Float>> out = new HashMap<>();
        QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
        try {
            for (Object recObj : wrapper.getRowRecords()) {
                QueryResultsWrapper.RowRecord row = (QueryResultsWrapper.RowRecord) recObj;
                Object idObj = row.get("id");
                Object embObj = row.get("embedding");
                long id = ((Number) idObj).longValue();
                List<Float> vec = toFloatList(embObj);
                if (!vec.isEmpty()) {
                    out.put(id, vec);
                }
            }
        } catch (Exception ex) {
            log.warn("Parse Milvus query rows failed: {}", ex.toString());
        }
        return out;
    }

    private List<Float> toFloatList(Object embObj) {
        if (embObj instanceof List<?> list) {
            List<Float> v = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Number n) {
                    v.add(n.floatValue());
                }
            }
            return v;
        }
        return Collections.emptyList();
    }

    /**
     * 单次 ANN search：对 queryChunkId 的向量找 TopK 邻居，过滤低于阈值与自环。
     *
     * @return 本批准备落库的边条数（已规范化 source &lt; target）
     */
    private int searchAndCollectEdges(MilvusServiceClient client,
                                      String collection,
                                      MetricType metricType,
                                      long queryChunkId,
                                      List<Float> vector,
                                      int topK,
                                      double minScore,
                                      List<KnowledgeGraphEdge> buffer,
                                      Set<String> seenPairs) {
        List<List<Float>> vectors = Collections.singletonList(vector);
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(metricType)
                .withTopK(topK)
                .withFloatVectors(vectors)
                .withVectorFieldName("embedding")
                .withOutFields(Collections.singletonList("id"))
                .withParams("{\"nprobe\":16}")
                .build();

        R<SearchResults> resp = client.search(searchParam);
        if (resp.getStatus() != R.Status.Success.getCode()) {
            log.debug("Milvus search failed for chunk {}: {}", queryChunkId, resp.getMessage());
            return 0;
        }
        // 本 SDK 中 SearchResults#getResults() 即单查询对应的 SearchResultData
        SearchResultsWrapper w = new SearchResultsWrapper(resp.getData().getResults());
        List<?> hits = w.getIDScore(0);
        if (hits == null || hits.isEmpty()) {
            return 0;
        }
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Object o : hits) {
            SearchResultsWrapper.IDScore hit = (SearchResultsWrapper.IDScore) o;
            long hitId = hit.getLongID();
            if (hitId == queryChunkId) {
                continue;
            }
            float score = hit.getScore();
            if (score < minScore) {
                continue;
            }
            long a = Math.min(queryChunkId, hitId);
            long b = Math.max(queryChunkId, hitId);
            String pairKey = a + ":" + b;
            if (!seenPairs.add(pairKey)) {
                continue;
            }
            KnowledgeGraphEdge edge = new KnowledgeGraphEdge();
            edge.setId(snowflakeIdWorker.nextId());
            edge.setSourceChunkId(a);
            edge.setTargetChunkId(b);
            edge.setSimilarityScore((double) score);
            edge.setCreatedAt(now);
            buffer.add(edge);
            n++;
        }
        return n;
    }
}
