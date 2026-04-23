CREATE DATABASE IF NOT EXISTS waibao
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE waibao;

CREATE TABLE IF NOT EXISTS user_user (
  id BIGINT NOT NULL COMMENT '雪花ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录名',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  real_name VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
  role VARCHAR(32) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/TEACHER/ADMIN',
  email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  last_login_at DATETIME(3) DEFAULT NULL COMMENT '最后登录时间',
  gmt_created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  KEY idx_user_tenant_role (tenant_id, role),
  KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS experiment_record (
  id BIGINT NOT NULL COMMENT '雪花ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  experiment_type VARCHAR(64) NOT NULL COMMENT '实验类型/操作类型',
  experiment_title VARCHAR(200) DEFAULT NULL COMMENT '实验标题',
  prompt_text TEXT DEFAULT NULL COMMENT '原始Prompt',
  input_payload JSON DEFAULT NULL COMMENT '输入参数/操作日志JSON',
  output_payload JSON DEFAULT NULL COMMENT '输出结果JSON',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'CREATED/RUNNING/SUCCESS/FAILED',
  model_name VARCHAR(128) DEFAULT NULL COMMENT '使用模型',
  tokens_in INT DEFAULT NULL COMMENT '输入token数',
  tokens_out INT DEFAULT NULL COMMENT '输出token数',
  started_at DATETIME(3) DEFAULT NULL COMMENT '开始时间',
  finished_at DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  gmt_created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (id),
  KEY idx_exp_user_created (user_id, gmt_created),
  KEY idx_exp_type (experiment_type),
  KEY idx_exp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实验记录表';

CREATE TABLE IF NOT EXISTS rag_document (
  id BIGINT NOT NULL COMMENT '雪花ID',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
  owner_user_id BIGINT NOT NULL COMMENT '归属用户ID',
  teacher_user_id BIGINT DEFAULT NULL COMMENT '教师ID，公共知识库时使用',
  kb_scope VARCHAR(32) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/PUBLIC',
  is_public TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公共知识库文档',
  category VARCHAR(64) NOT NULL DEFAULT 'general' COMMENT '知识分类，如math/coding/english',
  title VARCHAR(255) NOT NULL COMMENT '文档标题',
  doc_type VARCHAR(32) NOT NULL COMMENT 'TEXT/PDF/WEBPAGE',
  source_uri VARCHAR(1024) DEFAULT NULL COMMENT '来源地址',
  content_hash CHAR(64) DEFAULT NULL COMMENT '内容Hash',
  raw_content LONGTEXT DEFAULT NULL COMMENT '原始内容',
  chunk_strategy VARCHAR(128) DEFAULT NULL COMMENT '切分策略',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT '分块数量',
  milvus_collection VARCHAR(128) DEFAULT NULL COMMENT 'Milvus 集合名',
  status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/INDEXED/FAILED',
  meta JSON DEFAULT NULL COMMENT '文档元信息',
  gmt_created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_owner_hash (owner_user_id, content_hash),
  KEY idx_doc_tenant_scope (tenant_id, kb_scope),
  KEY idx_doc_tenant_public_category (tenant_id, is_public, category),
  KEY idx_doc_owner_status (owner_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 文档表';

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
  KEY idx_chunk_milvus_entity (milvus_entity_id),
  KEY idx_chunk_doc_milvus (document_id, milvus_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 文档分块表';

-- 知识图谱边：由 Chunk 向量相似度（离线任务）自动生成，避免手工连线
CREATE TABLE IF NOT EXISTS knowledge_graph_edge (
  id BIGINT NOT NULL COMMENT '雪花ID',
  source_chunk_id BIGINT NOT NULL COMMENT '源 chunk（rag_document_chunk.id，且约定 source < target）',
  target_chunk_id BIGINT NOT NULL COMMENT '目标 chunk',
  similarity_score DOUBLE NOT NULL COMMENT '余弦相似度或 Milvus COSINE 得分，越大越相似',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '写入时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_kg_edge_pair (source_chunk_id, target_chunk_id),
  KEY idx_kg_edge_score (similarity_score),
  KEY idx_kg_edge_source (source_chunk_id),
  KEY idx_kg_edge_target (target_chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG Chunk 向量相似度知识图谱边';

-- 向后兼容：已存在库升级字段
ALTER TABLE rag_document
  ADD COLUMN IF NOT EXISTS is_public TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公共知识库文档' AFTER kb_scope,
  ADD COLUMN IF NOT EXISTS category VARCHAR(64) NOT NULL DEFAULT 'general' COMMENT '知识分类，如math/coding/english' AFTER is_public;

