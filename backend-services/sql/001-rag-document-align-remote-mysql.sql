-- =============================================================================
-- rag_document / rag_document_chunk 与 backend 代码对齐（远程库一次执行）
-- 适用：Navicat / mysql 客户端；不依赖 ADD COLUMN IF NOT EXISTS（避免 1064）
-- 用法：整段复制到「查询」窗口执行一次即可；已存在的列/索引会跳过（SELECT 1）
-- =============================================================================

USE waibao;

-- ---------------------------------------------------------------------------
-- 工具：仅当列不存在时执行 ALTER ADD COLUMN
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_add_column_if_missing;

DELIMITER $$

CREATE PROCEDURE sp_add_column_if_missing(
  IN p_table VARCHAR(64),
  IN p_column VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- rag_document：按 000-schema.sql 顺序补齐（已存在则跳过）
-- ---------------------------------------------------------------------------
CALL sp_add_column_if_missing('rag_document', 'tenant_id', 'BIGINT NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL sp_add_column_if_missing('rag_document', 'owner_user_id', 'BIGINT NOT NULL COMMENT ''归属用户ID''');
CALL sp_add_column_if_missing('rag_document', 'teacher_user_id', 'BIGINT DEFAULT NULL COMMENT ''教师ID，公共知识库时使用''');
CALL sp_add_column_if_missing('rag_document', 'kb_scope', 'VARCHAR(32) NOT NULL DEFAULT ''PRIVATE'' COMMENT ''PRIVATE/PUBLIC''');
CALL sp_add_column_if_missing('rag_document', 'is_public', 'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否公共知识库文档''');
CALL sp_add_column_if_missing('rag_document', 'category', 'VARCHAR(64) NOT NULL DEFAULT ''general'' COMMENT ''知识分类''');
CALL sp_add_column_if_missing('rag_document', 'title', 'VARCHAR(255) NOT NULL COMMENT ''文档标题''');
CALL sp_add_column_if_missing('rag_document', 'doc_type', 'VARCHAR(32) NOT NULL COMMENT ''TEXT/PDF/WEBPAGE''');
CALL sp_add_column_if_missing('rag_document', 'source_uri', 'VARCHAR(1024) DEFAULT NULL COMMENT ''来源地址''');
CALL sp_add_column_if_missing('rag_document', 'content_hash', 'CHAR(64) DEFAULT NULL COMMENT ''内容Hash''');
CALL sp_add_column_if_missing('rag_document', 'raw_content', 'LONGTEXT DEFAULT NULL COMMENT ''原始内容''');
CALL sp_add_column_if_missing('rag_document', 'chunk_strategy', 'VARCHAR(128) DEFAULT NULL COMMENT ''切分策略''');
CALL sp_add_column_if_missing('rag_document', 'chunk_count', 'INT NOT NULL DEFAULT 0 COMMENT ''分块数量''');
CALL sp_add_column_if_missing('rag_document', 'milvus_collection', 'VARCHAR(128) DEFAULT NULL COMMENT ''Milvus 集合名''');
CALL sp_add_column_if_missing('rag_document', 'status', 'VARCHAR(32) NOT NULL DEFAULT ''UPLOADED'' COMMENT ''UPLOADED/INDEXED/FAILED''');
CALL sp_add_column_if_missing('rag_document', 'meta', 'JSON DEFAULT NULL COMMENT ''文档元信息''');
CALL sp_add_column_if_missing('rag_document', 'gmt_created', 'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间''');
CALL sp_add_column_if_missing('rag_document', 'gmt_modified', 'DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''修改时间''');

DROP PROCEDURE IF EXISTS sp_add_column_if_missing;

-- ---------------------------------------------------------------------------
-- 索引：不存在则创建（避免 1061 Duplicate key）
-- ---------------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rag_document'
    AND INDEX_NAME = 'idx_doc_tenant_scope'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE INDEX idx_doc_tenant_scope ON rag_document (tenant_id, kb_scope)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rag_document'
    AND INDEX_NAME = 'idx_doc_tenant_public_category'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE INDEX idx_doc_tenant_public_category ON rag_document (tenant_id, is_public, category)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rag_document'
    AND INDEX_NAME = 'idx_doc_owner_status'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE INDEX idx_doc_owner_status ON rag_document (owner_user_id, status)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- UNIQUE( owner_user_id, content_hash )：若不存在则尝试添加（已有重复数据会失败，需人工处理）
SET @idx_exists := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'rag_document'
    AND INDEX_NAME = 'uk_doc_owner_hash'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE UNIQUE INDEX uk_doc_owner_hash ON rag_document (owner_user_id, content_hash)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- rag_document_chunk：与 000-schema.sql 对齐（表不存在则整表创建）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rag_document_chunk (
  id BIGINT NOT NULL COMMENT '雪花ID / chunk_id',
  document_id BIGINT NOT NULL COMMENT '文档ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  owner_user_id BIGINT NOT NULL COMMENT '归属用户ID',
  chunk_index INT NOT NULL COMMENT '分块顺序',
  content MEDIUMTEXT NOT NULL COMMENT '分块内容',
  token_count INT DEFAULT NULL COMMENT 'token数量',
  milvus_entity_id VARCHAR(64) DEFAULT NULL COMMENT 'Milvus 主键映射',
  meta JSON DEFAULT NULL COMMENT 'chunk 元信息',
  gmt_created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chunk_doc_index (document_id, chunk_index),
  KEY idx_chunk_doc (document_id),
  KEY idx_chunk_owner (owner_user_id),
  KEY idx_chunk_milvus_entity (milvus_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 文档分块表';

-- 验证（可选，执行后看结果）
-- SHOW COLUMNS FROM rag_document;
-- SHOW INDEX FROM rag_document;
