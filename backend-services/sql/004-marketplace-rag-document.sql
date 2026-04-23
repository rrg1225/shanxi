-- =============================================================================
-- 资源广场：rag_document 分类 / 公开字段 + 内置专业电子书（Transformer / RAG / Agent）
-- =============================================================================
-- 说明：
-- - 若库已由 000-schema.sql 或 001-rag-document-align-remote-mysql.sql 初始化，
--   则 is_public、category 列通常已存在；此时仅执行下方「种子数据」段即可。
-- - 老库缺列时，取消注释「可选：列补齐」段（单列报错 Duplicate column 可忽略）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 可选：列补齐（MySQL：列已存在会报错，可跳过本段）
-- ---------------------------------------------------------------------------
-- ALTER TABLE rag_document
--   ADD COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公开（资源广场等公共检索）' AFTER kb_scope;
--
-- ALTER TABLE rag_document
--   ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'general' COMMENT '资源分类（如：学习/编程/求职）' AFTER is_public;
--
-- CREATE INDEX idx_doc_tenant_public_category ON rag_document (tenant_id, is_public, category);

-- ---------------------------------------------------------------------------
-- 内置 5 本专业电子书（公开、带 meta：summary / previewImageUrl / knowledgeNodeId）
-- 与前端知识图谱节点 id 对齐：nlp-embed, rag-pipeline, agent-orchestration, vector-db 等
-- ---------------------------------------------------------------------------
INSERT INTO rag_document (
  id,
  tenant_id,
  owner_user_id,
  teacher_user_id,
  kb_scope,
  is_public,
  category,
  title,
  doc_type,
  source_uri,
  content_hash,
  chunk_strategy,
  chunk_count,
  milvus_collection,
  status,
  meta,
  gmt_created,
  gmt_modified
) VALUES
(
  9200000000000000101,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  '学习',
  '《Transformer 与自注意力机制》精要（公开版）',
  'TEXT',
  'preset://marketplace/ebook-transformer-attention',
  'seed_mkt_tf_0000000000000000000000000000000000000000000000000000000000000001',
  'preset',
  14,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeId', 'nlp-embed',
    'knowledgeNodeIds', JSON_ARRAY('nlp-embed'),
    'previewImageUrl', 'https://picsum.photos/seed/mkt-transformer/320/420',
    'summary', '从 Encoder-Decoder 到纯 Encoder：Self-Attention、多头注意力与位置编码，衔接大模型表示学习。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  9200000000000000102,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  '学习',
  '《RAG 检索增强生成》系统实践手册',
  'TEXT',
  'preset://marketplace/ebook-rag-systems',
  'seed_mkt_rag_0000000000000000000000000000000000000000000000000000000000000002',
  'preset',
  16,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeId', 'rag-pipeline',
    'knowledgeNodeIds', JSON_ARRAY('rag-pipeline'),
    'previewImageUrl', 'https://picsum.photos/seed/mkt-rag-book/320/420',
    'summary', '切分、向量化、召回、重排与引用生成：构建可观测、可评测的企业级 RAG 流水线。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  9200000000000000103,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  '编程',
  '《LLM Agent：工具调用与任务分解》',
  'TEXT',
  'preset://marketplace/ebook-llm-agent-tools',
  'seed_mkt_agt_0000000000000000000000000000000000000000000000000000000000000003',
  'preset',
  11,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeId', 'agent-orchestration',
    'knowledgeNodeIds', JSON_ARRAY('agent-orchestration'),
    'previewImageUrl', 'https://picsum.photos/seed/mkt-agent-tools/320/420',
    'summary', 'Function Calling、ReAct 与规划循环：把大模型变成可调工具的自主智能体。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  9200000000000000104,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  '编程',
  '《多智能体编排与协作模式》',
  'TEXT',
  'preset://marketplace/ebook-multi-agent',
  'seed_mkt_ma_0000000000000000000000000000000000000000000000000000000000000004',
  'preset',
  10,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeId', 'agent-orchestration',
    'knowledgeNodeIds', JSON_ARRAY('agent-orchestration', 'rag-pipeline'),
    'previewImageUrl', 'https://picsum.photos/seed/mkt-multi-agent/320/420',
    'summary', '角色分工、黑板/消息总线与监督者模式：复杂任务下的多 Agent 可靠协作。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  9200000000000000105,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  '求职',
  '《大模型工程师面试：Transformer / RAG / Agent》',
  'TEXT',
  'preset://marketplace/ebook-llm-interview',
  'seed_mkt_iv_0000000000000000000000000000000000000000000000000000000000000005',
  'preset',
  9,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeId', 'nlp-embed',
    'knowledgeNodeIds', JSON_ARRAY('nlp-embed', 'rag-pipeline', 'agent-orchestration'),
    'previewImageUrl', 'https://picsum.photos/seed/mkt-llm-interview/320/420',
    'summary', '高频八股与白板题：Attention 复杂度、RAG 失败案例归因、Agent 安全与评测指标。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  category = VALUES(category),
  is_public = VALUES(is_public),
  kb_scope = VALUES(kb_scope),
  meta = VALUES(meta),
  chunk_count = VALUES(chunk_count),
  gmt_modified = CURRENT_TIMESTAMP(3);
