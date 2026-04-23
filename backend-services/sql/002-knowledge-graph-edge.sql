-- 远程库一次性执行：知识图谱边表（若已存在于 000-schema 同步则可跳过）
USE waibao;

CREATE TABLE IF NOT EXISTS knowledge_graph_edge (
  id BIGINT NOT NULL COMMENT '雪花ID',
  source_chunk_id BIGINT NOT NULL COMMENT '源 chunk（约定 source < target）',
  target_chunk_id BIGINT NOT NULL COMMENT '目标 chunk',
  similarity_score DOUBLE NOT NULL COMMENT '相似度得分',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_kg_edge_pair (source_chunk_id, target_chunk_id),
  KEY idx_kg_edge_score (similarity_score),
  KEY idx_kg_edge_source (source_chunk_id),
  KEY idx_kg_edge_target (target_chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG Chunk 向量相似度知识图谱边';
