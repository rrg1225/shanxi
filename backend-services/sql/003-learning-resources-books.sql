-- 学习资源模块：确保 rag_document 含 is_public、category（与 000-schema / 001 对齐）
-- 若历史库缺列，可取消注释以下语句（已存在列时会报错，请先执行 001 中的 sp_add_column 脚本）：
--
-- ALTER TABLE rag_document
--   ADD COLUMN is_public TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公共知识库文档',
--   ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'general' COMMENT '知识分类';
--
-- 推荐：直接执行 001-rag-document-align-remote-mysql.sql，以幂等方式补齐列与索引。

-- 示例：5 条大模型相关公共书籍（ID 1600+），category 使用 transformer / rag / agent
-- 执行前若已存在相同 id，请先 DELETE FROM rag_document WHERE id BETWEEN 1601 AND 1605;

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
  1601,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  'transformer',
  'Attention Is All You Need 精读笔记（Transformer 架构）',
  'TEXT',
  'preset://books/transformer-attention',
  'lr_seed_transformer_1601_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  'preset',
  16,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeIds', JSON_ARRAY('nlp-embed'),
    'summary', '自注意力与编码器-解码器结构，对应图谱「文本表示与向量嵌入」节点。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  1602,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  'rag',
  'RAG 检索增强生成：切分、向量库与重排实战',
  'TEXT',
  'preset://books/rag-practice',
  'lr_seed_rag_1602_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
  'preset',
  20,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeIds', JSON_ARRAY('rag-pipeline'),
    'summary', '从文档入库到召回链路，对齐「RAG 检索增强链路」节点。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  1603,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  'agent',
  '多智能体编排与工具调用设计指南',
  'TEXT',
  'preset://books/agent-orchestration',
  'lr_seed_agent_1603_cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
  'preset',
  14,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeIds', JSON_ARRAY('agent-orchestration'),
    'summary', '角色、状态机与函数调用，对齐「多智能体编排」节点。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  1604,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  'transformer',
  'LLM 预训练与 Scaling Laws 导读',
  'TEXT',
  'preset://books/scaling-laws',
  'lr_seed_transformer_1604_dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
  'preset',
  12,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeIds', JSON_ARRAY('nlp-embed', 'course-project'),
    'summary', '规模、数据与算力的标度关系，可作为进阶阅读。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
),
(
  1605,
  1,
  2001,
  2001,
  'PUBLIC',
  1,
  'rag',
  '向量数据库与近似最近邻：工程权衡手册',
  'TEXT',
  'preset://books/vector-ann',
  'lr_seed_rag_1605_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
  'preset',
  18,
  NULL,
  'INDEXED',
  JSON_OBJECT(
    'knowledgeNodeIds', JSON_ARRAY('vector-db', 'rag-pipeline'),
    'summary', 'HNSW/IVF 与混合检索，衔接 RAG 与向量库节点。'
  ),
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
);
