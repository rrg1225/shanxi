-- 复合索引：按 document_id 过滤并关联 milvus_entity_id（列名对应代码中的 Milvus 主键，非 vector_id）
USE waibao;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rag_document_chunk'
    AND INDEX_NAME = 'idx_chunk_doc_milvus'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE INDEX idx_chunk_doc_milvus ON rag_document_chunk (document_id, milvus_entity_id)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
