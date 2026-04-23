# AI Gateway (FastAPI)

提供三类核心接口：

1. `/api/ai/prompt-test`：Qwen 流式 Prompt 测试（SSE）
2. `/api/ai/document-process`：文本清洗 -> Chunking -> Embedding -> 写入 Milvus
3. `/api/ai/rag-search`：Query 向量化 -> Milvus 相似度检索 -> 返回 Top-K + `coords`

## 运行

```bash
cd ai-gateway
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## 环境变量（建议）

```text
# Milvus
MILVUS_HOST=127.0.0.1
MILVUS_PORT=19530
MILVUS_COLLECTION=rag_chunks

# Qwen / Embedding：OpenAI-compatible 网关（本地或云端都可）
QWEN_BASE_URL=http://localhost:8001/v1
QWEN_API_KEY=your-key
QWEN_MODEL=qwen-plus

EMBEDDING_BASE_URL=http://localhost:8001/v1
EMBEDDING_API_KEY=your-key
EMBEDDING_MODEL=text-embedding-3-small
# 可选：如果不填，系统会首次探测 embedding 维度
EMBEDDING_DIM=1536

# Snowflake
SNOWFLAKE_WORKER_ID=1
SNOWFLAKE_DATACENTER_ID=1

# CORS
CORS_ALLOW_ORIGINS=*
```

