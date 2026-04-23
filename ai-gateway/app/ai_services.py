import asyncio
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, AsyncGenerator, Dict, List, Optional, Tuple

if __package__ is None:
    _root = Path(__file__).resolve().parent.parent
    if str(_root) not in sys.path:
        sys.path.insert(0, str(_root))

os.environ.setdefault("TRANSFORMERS_NO_ADVISORY_WARNINGS", "1")

import numpy as np
from openai import OpenAI
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    utility,
)
from pymilvus import connections as milvus_connections

from app.snowflake import SnowflakeIdGenerator

@dataclass(frozen=True)
class Settings:
    # Milvus
    MILVUS_HOST: str = os.getenv("MILVUS_HOST", "in03-339eedee4650ed0.serverless.aws-eu-central-1.cloud.zilliz.com")
    MILVUS_PORT: str = os.getenv("MILVUS_PORT", "19530")
    # 兼容你给的 MILVUS_USER 命名
    MILVUS_USERNAME: Optional[str] = os.getenv("MILVUS_USERNAME") or os.getenv("MILVUS_USER")
    MILVUS_PASSWORD: Optional[str] = os.getenv("MILVUS_PASSWORD")
    MILVUS_SECURE: bool = os.getenv("MILVUS_SECURE", "true").strip().lower() in ("1", "true", "yes", "y")
    MILVUS_COLLECTION: str = os.getenv("MILVUS_COLLECTION", "career_knowledge_base")
    MILVUS_METRIC_TYPE: str = os.getenv("MILVUS_METRIC_TYPE", "COSINE")

    # Embeddings (OpenAI-compatible)
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "text-embedding-v3")
    EMBEDDING_BASE_URL: Optional[str] = os.getenv("EMBEDDING_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    # 兼容你给的 ALIYUN_API_KEY 命名
    EMBEDDING_API_KEY: Optional[str] = os.getenv("EMBEDDING_API_KEY") or os.getenv("ALIYUN_API_KEY")
    EMBEDDING_DIM: Optional[int] = int(os.getenv("EMBEDDING_DIM", "1024")) or None

    # Qwen LLM (OpenAI-compatible)
    QWEN_MODEL: str = os.getenv("QWEN_MODEL", "qwen3-max")
    QWEN_BASE_URL: Optional[str] = os.getenv("QWEN_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
    QWEN_API_KEY: Optional[str] = os.getenv("QWEN_API_KEY") or os.getenv("ALIYUN_API_KEY")
    QWEN_SYSTEM_PROMPT: str = os.getenv(
        "QWEN_SYSTEM_PROMPT",
        "你是一个严谨的AI拆解师。回答简洁、结构化，并按用户意图生成内容。",
    )

    # Decomposition（1024->3）方式：PCA 或 t-SNE
    DECOMP_METHOD: str = os.getenv("DECOMP_METHOD", "PCA").strip().upper()
    DECOMP_SCALE_TO: float = float(os.getenv("DECOMP_SCALE_TO", "60.0"))

    # Snowflake
    SNOWFLAKE_WORKER_ID: int = int(os.getenv("SNOWFLAKE_WORKER_ID", "1"))
    SNOWFLAKE_DATACENTER_ID: int = int(os.getenv("SNOWFLAKE_DATACENTER_ID", "1"))


def clean_text(raw: str) -> str:
    """
    文本清洗：
    - 统一换行
    - 去除 BOM
    - 删除控制字符（保留 \n 和 \t）
    - 折叠多重空白
    - 压缩连续空行
    """
    if not raw:
        return ""

    s = raw.replace("\ufeff", "")  # BOM
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    # 删除除 \n \t 以外的控制字符
    s = re.sub(r"[^\S\r\n\t]+", " ", s)  # 折叠非换行空白
    s = re.sub(r"[^\x20-\x7E\u4E00-\u9FFF\n\t]+", "", s)  # 粗略保留可见字符与中文
    s = re.sub(r"\n{3,}", "\n\n", s)  # 连续空行压缩
    return s.strip()


def chunk_text(
    text: str,
    chunk_size: int = 800,
    chunk_overlap: int = 150,
    separators: Optional[List[str]] = None,
) -> List[str]:
    separators = separators or ["\n\n", "\n", "。", "！", "？", ".", "!", "?", " ", ""]
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        separators=separators,
    )
    return [c for c in splitter.split_text(text) if c.strip()]


def vector_to_coords(vector: List[float], dims: int = 3) -> List[float]:
    """
    单向量降维的最简 fallback：
    - 仍然取前 `dims` 维（当无法对一组向量做 PCA/t-SNE 时使用）
    """
    arr = list(vector)
    if len(arr) >= dims:
        return [float(x) for x in arr[:dims]]
    # 不足则补 0
    return [float(x) for x in arr] + [0.0] * (dims - len(arr))


def pca_project(embeddings: List[List[float]], n_components: int = 3, scale_to: float = 60.0) -> List[List[float]]:
    """
    真实 PCA 降维（1024维 -> 3维）实现（仅依赖 numpy）。

    使用 SVD：
      X_centered = U * S * Vt
      principal coordinates = X_centered @ Vt.T[:,:k] == U[:,:k] * S[:k]
    """
    if not embeddings:
        return []

    try:
        X = np.asarray(embeddings, dtype=np.float32)
    except Exception:
        return [vector_to_coords(v, n_components) for v in embeddings]
    if X.ndim != 2:
        return [vector_to_coords(v, n_components) for v in embeddings]

    n_samples, _ = X.shape
    if n_samples < 2:
        return [vector_to_coords(v, n_components) for v in embeddings]

    try:
        X_centered = X - X.mean(axis=0, keepdims=True)
        # full_matrices=False：避免不必要的大矩阵
        U, S, _ = np.linalg.svd(X_centered, full_matrices=False)
        k = min(n_components, U.shape[1])
        coords = U[:, :k] * S[:k]
    except Exception:
        return [vector_to_coords(v, n_components) for v in embeddings]

    # 归一化到便于前端可视化的尺度
    max_abs = float(np.max(np.abs(coords))) if coords.size else 0.0
    if max_abs > 1e-12:
        coords = coords / max_abs * float(scale_to)

    # 不足 k 的补零
    if k < n_components:
        pad = np.zeros((n_samples, n_components - k), dtype=np.float32)
        coords = np.concatenate([coords, pad], axis=1)

    return coords.astype(float).tolist()


def tsne_project(
    embeddings: List[List[float]],
    n_components: int = 3,
    scale_to: float = 60.0,
) -> List[List[float]]:
    """
    t-SNE 降维（可选）。

    注意：t-SNE 计算成本高，chunk 数较大时可能慢。
    如果运行失败，自动降级到 PCA。
    """
    try:
        from sklearn.manifold import TSNE  # type: ignore
    except Exception:
        return pca_project(embeddings, n_components=n_components, scale_to=scale_to)

    if not embeddings:
        return []
    try:
        X = np.asarray(embeddings, dtype=np.float32)
    except Exception:
        return [vector_to_coords(v, n_components) for v in embeddings]

    if X.ndim != 2 or X.shape[0] < 2:
        return pca_project(embeddings, n_components=n_components, scale_to=scale_to)

    try:
        perplexity = min(30.0, max(5.0, (X.shape[0] - 1) / 3.0))
        tsne = TSNE(
            n_components=n_components,
            perplexity=perplexity,
            init="random",
            learning_rate="auto",
            random_state=42,
        )
        coords = tsne.fit_transform(X)

        max_abs = float(np.max(np.abs(coords))) if coords.size else 0.0
        if max_abs > 1e-12:
            coords = coords / max_abs * float(scale_to)
        return coords.astype(float).tolist()
    except Exception:
        return pca_project(embeddings, n_components=n_components, scale_to=scale_to)


def decompose_project(
    embeddings: List[List[float]],
    method: str,
    n_components: int = 3,
    scale_to: float = 60.0,
) -> List[List[float]]:
    method = (method or "PCA").strip().upper()
    if method in ("TSNE", "T-SNE", "T_SNE"):
        return tsne_project(embeddings, n_components=n_components, scale_to=scale_to)
    return pca_project(embeddings, n_components=n_components, scale_to=scale_to)


class QwenStreamingClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    def _build_llm(self, temperature: float, top_p: float) -> ChatOpenAI:
        # Qwen 通过 OpenAI-compatible 网关/本地服务时，这种方式最稳。
        # 若你们是 DashScope 专有 SDK，可在这里二次实现。
        return ChatOpenAI(
            model=self.settings.QWEN_MODEL,
            temperature=temperature,
            top_p=top_p,
            streaming=True,
            openai_api_key=self.settings.QWEN_API_KEY or "EMPTY",
            openai_api_base=self.settings.QWEN_BASE_URL,
        )

    async def stream_prompt(
        self,
        prompt: str,
        temperature: float,
        top_p: float,
    ) -> AsyncGenerator[str, None]:
        # 未配置密钥时走本地演示输出，避免导师 / prompt-test 直接报错（开发环境友好）
        api_key = (self.settings.QWEN_API_KEY or "").strip()
        if not api_key or api_key == "EMPTY":
            text = self._mock_tutor_reply(prompt)
            chunk_size = 6
            for i in range(0, len(text), chunk_size):
                yield text[i : i + chunk_size]
                await asyncio.sleep(0.015)
            return

        llm = self._build_llm(temperature=temperature, top_p=top_p)

        messages = [
            SystemMessage(content=self.settings.QWEN_SYSTEM_PROMPT),
            HumanMessage(content=prompt),
        ]

        # LangChain 会按流式返回 chunk
        async for chunk in llm.astream(messages):
            token = getattr(chunk, "content", None)
            if token:
                yield token

    def _mock_tutor_reply(self, prompt: str) -> str:
        p = (prompt or "").strip()
        lines = [
            "【离线演示】当前未配置 QWEN_API_KEY（或 ALIYUN_API_KEY），网关返回固定说明，便于联调界面。",
            "配置密钥并重启 ai-gateway 后即可调用真实模型。",
            "",
        ]
        if "RAG" in p or "检索" in p or "分数" in p:
            lines.extend(
                [
                    "关于 RAG 检索分数：",
                    "1）常见为向量相似度（余弦/点积），数值越大通常表示与查询越相关；",
                    "2）不同向量库/归一化方式会导致分数区间不同，应看「排序」而非绝对值；",
                    "3）建议结合重排（rerank）与人工抽检，避免单一阈值误杀。",
                ]
            )
        else:
            lines.extend(
                [
                    "学习建议：先明确当前模块目标，再对照文档或图谱节点逐步完成；",
                    "遇到报错时把报错原文与操作步骤记下来，便于排查。",
                ]
            )
        return "\n".join(lines)

    def _build_non_stream_llm(self, temperature: float, top_p: float) -> ChatOpenAI:
        return ChatOpenAI(
            model=self.settings.QWEN_MODEL,
            temperature=temperature,
            top_p=top_p,
            streaming=False,
            openai_api_key=self.settings.QWEN_API_KEY or "EMPTY",
            openai_api_base=self.settings.QWEN_BASE_URL,
        )

    async def generate_probability_tree(
        self,
        prompt: str,
        temperature: float,
        top_p: float,
    ) -> Dict[str, Any]:
        instruction = (
            "请基于用户输入，构造一棵'词汇预测概率树'并只输出 JSON。"
            "JSON 格式必须为："
            '{"token":"ROOT","probability":1,"children":[{"token":"词","probability":0.42,"children":[...]}]}。'
            "要求："
            "1. 根节点 token 固定为 ROOT；"
            "2. 第一层返回 3-5 个候选词；"
            "3. 每个第一层节点再给 2-4 个 children；"
            "4. probability 用 0-1 小数；"
            "5. 同层概率大致递减，内容贴合用户输入；"
            "6. 不要输出 markdown，不要输出解释。"
        )
        messages = [
            SystemMessage(content=instruction),
            HumanMessage(content=prompt),
        ]
        llm = self._build_non_stream_llm(temperature=temperature, top_p=top_p)
        try:
            response = await llm.ainvoke(messages)
            content = getattr(response, "content", "") or ""
            cleaned = content.strip()
            cleaned = re.sub(r"^```json\s*", "", cleaned)
            cleaned = re.sub(r"```$", "", cleaned).strip()
            parsed = json.loads(cleaned)
            if isinstance(parsed, dict) and "token" in parsed and "probability" in parsed:
                return parsed
        except Exception:
            pass

        # fallback：保证前端组件永远有可渲染结果
        return {
            "token": "ROOT",
            "probability": 1,
            "children": [
                {
                    "token": "RAG",
                    "probability": 0.42,
                    "children": [
                        {"token": "检索", "probability": 0.19},
                        {"token": "增强", "probability": 0.13},
                        {"token": "知识库", "probability": 0.10},
                    ],
                },
                {
                    "token": "实验",
                    "probability": 0.33,
                    "children": [
                        {"token": "流程", "probability": 0.15},
                        {"token": "节点", "probability": 0.10},
                        {"token": "设计", "probability": 0.08},
                    ],
                },
                {
                    "token": "推理",
                    "probability": 0.25,
                    "children": [
                        {"token": "拆解", "probability": 0.11},
                        {"token": "可视化", "probability": 0.08},
                        {"token": "分析", "probability": 0.06},
                    ],
                },
            ],
        }

    async def evaluate_prompt_ab_outputs(
        self,
        *,
        user_task: str,
        output_a: str,
        output_b: str,
        params_a: Optional[Dict[str, Any]] = None,
        params_b: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Prompt 实验室 A/B：使用当前 QWEN_MODEL（默认 qwen3-max）作裁判，对两份输出做「准确性 / 简洁度」打分。
        """
        params_a = params_a or {}
        params_b = params_b or {}
        api_key = (self.settings.QWEN_API_KEY or "").strip()
        if not api_key or api_key == "EMPTY":
            return {
                "verdict": "tie",
                "judgeModel": self.settings.QWEN_MODEL,
                "scores": {
                    "A": {"accuracy": 7.0, "conciseness": 7.0},
                    "B": {"accuracy": 7.0, "conciseness": 7.0},
                },
                "reason": "【离线演示】未配置 QWEN_API_KEY（或 ALIYUN_API_KEY），返回固定分数。",
                "mock": True,
            }

        system = (
            "你是严谨、公正的文本评测裁判。用户给出「任务说明」与两份模型输出（方案A、方案B）。"
            "请比较 A/B：1）准确性：是否切题、逻辑自洽、无明显事实错误；2）简洁度：是否少废话、结构清晰。"
            "只输出一个 JSON 对象，键名必须为：verdict（字符串，取值仅为 A / B / tie 三者之一）、"
            "scores（对象，含子键 A 与 B；每个子键为对象，含 accuracy 与 conciseness，0 到 10 的数值）、"
            "reason（字符串，中文一到三句简要理由）。"
            "不要 markdown，不要代码块，不要其它文字。"
        )
        human = (
            f"【用户任务】\n{(user_task or '').strip()}\n\n"
            f"【方案A】采样参数：{json.dumps(params_a, ensure_ascii=False)}\n{(output_a or '').strip()}\n\n"
            f"【方案B】采样参数：{json.dumps(params_b, ensure_ascii=False)}\n{(output_b or '').strip()}"
        )
        messages = [
            SystemMessage(content=system),
            HumanMessage(content=human),
        ]
        llm = self._build_non_stream_llm(temperature=0.15, top_p=0.45)
        try:
            response = await llm.ainvoke(messages)
            content = getattr(response, "content", "") or ""
            cleaned = content.strip()
            cleaned = re.sub(r"^```json\s*", "", cleaned)
            cleaned = re.sub(r"```\s*$", "", cleaned).strip()
            parsed = json.loads(cleaned)
            if isinstance(parsed, dict) and isinstance(parsed.get("scores"), dict):
                parsed["judgeModel"] = self.settings.QWEN_MODEL
                parsed["mock"] = False
                return parsed
        except Exception as e:
            return {
                "verdict": "tie",
                "judgeModel": self.settings.QWEN_MODEL,
                "scores": {
                    "A": {"accuracy": 5.0, "conciseness": 5.0},
                    "B": {"accuracy": 5.0, "conciseness": 5.0},
                },
                "reason": f"裁判输出解析失败，请重试。{type(e).__name__}",
                "error": str(e)[:240],
                "mock": True,
            }

        return {
            "verdict": "tie",
            "judgeModel": self.settings.QWEN_MODEL,
            "scores": {
                "A": {"accuracy": 6.0, "conciseness": 6.0},
                "B": {"accuracy": 6.0, "conciseness": 6.0},
            },
            "reason": "未能从模型返回中解析有效 JSON，已回退为平局。",
            "mock": True,
        }


class EmbeddingClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        # 直接使用 openai-sdk 调用 OpenAI-compatible embedding 接口（比 langchain_openai 更稳）
        self._client = OpenAI(
            api_key=self.settings.EMBEDDING_API_KEY or "EMPTY",
            base_url=self.settings.EMBEDDING_BASE_URL,
        )

    async def embed_documents(self, texts: List[str]) -> List[List[float]]:
        # openai embeddings API 是同步实现，放到线程池避免阻塞事件循环
        texts = [t if isinstance(t, str) else str(t) for t in texts]
        if not texts:
            return []

        def _embed_batch(batch: List[str]) -> List[List[float]]:
            resp = self._client.embeddings.create(model=self.settings.EMBEDDING_MODEL, input=batch)
            return [d.embedding for d in resp.data]

        # 分批发送，避免一次请求太大
        out: List[List[float]] = []
        batch_size = 32
        for i in range(0, len(texts), batch_size):
            batch = texts[i : i + batch_size]
            out.extend(await asyncio.to_thread(_embed_batch, batch))
        return out

    async def embed_query(self, query: str) -> List[float]:
        vecs = await self.embed_documents([query])
        return vecs[0] if vecs else []

    async def detect_dimension(self) -> int:
        # 用一次小请求探测向量维度（只有首次会触发）
        vec = await self.embed_query("dimension-detect")
        return int(len(vec))


class MilvusRagRepository:
    """
    Milvus collection 约定：
    - embedding：FLOAT_VECTOR（dim = embeddings 维度）
    - chunk_text：VARCHAR（用于返回给前端）
    - visibility：1=教师公共 / 0=学生私有（也支持 teacher_user_id 字段）
    """

    def __init__(self, settings: Settings, embedding_client: EmbeddingClient, snowflake: SnowflakeIdGenerator):
        self.settings = settings
        self.embedding_client = embedding_client
        self.snowflake = snowflake
        self._collection: Optional[Collection] = None
        # 为了能在 rag-search 时直接返回 PCA 坐标（避免检索时再取 1024 维 embedding 导致 MilvusException）
        # 使用独立 collection，避免与历史旧 schema 冲突。
        # coords 用标量字段存（coord_x/coord_y/coord_z），避免 Milvus search 取回向量字段报错
        # 同时用版本化 collection 名称，避免旧 schema 影响。
        self._collection_name = f"{settings.MILVUS_COLLECTION}_pca3d_v2"
        self._embedding_dim: Optional[int] = settings.EMBEDDING_DIM

    def _connect_if_needed(self) -> None:
        # pymilvus 在连接失败时会抛异常，这里不做吞错
        milvus_connections.connect(
            alias="default",
            host=self.settings.MILVUS_HOST,
            port=self.settings.MILVUS_PORT,
            user=self.settings.MILVUS_USERNAME,
            password=self.settings.MILVUS_PASSWORD,
            secure=self.settings.MILVUS_SECURE,
        )

    def _build_schema(self, dim: int) -> CollectionSchema:
        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=False),
            FieldSchema(name="tenant_id", dtype=DataType.INT64, is_primary=False),
            FieldSchema(name="visibility", dtype=DataType.INT8, is_primary=False),  # 1 public, 0 private
            FieldSchema(name="owner_user_id", dtype=DataType.INT64, is_primary=False),
            FieldSchema(name="teacher_user_id", dtype=DataType.INT64, is_primary=False),
            FieldSchema(name="document_id", dtype=DataType.INT64, is_primary=False),
            FieldSchema(name="chunk_index", dtype=DataType.INT32, is_primary=False),
            FieldSchema(name="chunk_text", dtype=DataType.VARCHAR, max_length=8192),
            FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=dim),
            FieldSchema(name="coord_x", dtype=DataType.FLOAT, is_primary=False),
            FieldSchema(name="coord_y", dtype=DataType.FLOAT, is_primary=False),
            FieldSchema(name="coord_z", dtype=DataType.FLOAT, is_primary=False),
        ]
        return CollectionSchema(fields=fields, description="RAG chunk vectors")

    async def ensure_collection(self) -> Collection:
        if self._collection is not None:
            return self._collection

        self._connect_if_needed()
        dim = self._embedding_dim
        if not dim:
            dim = await self.embedding_client.detect_dimension()
            self._embedding_dim = dim

        if not utility.has_collection(self._collection_name):
            schema = self._build_schema(dim)
            self._collection = Collection(name=self._collection_name, schema=schema)
        else:
            self._collection = Collection(name=self._collection_name)

        # 建索引（只有第一次/索引不存在时才建）
        if not self._collection.indexes:
            index_params = {
                "index_type": "IVF_FLAT",
                "metric_type": self.settings.MILVUS_METRIC_TYPE,
                "params": {"nlist": 128},
            }
            self._collection.create_index(field_name="embedding", index_params=index_params)

        self._collection.load()
        return self._collection

    async def upsert_document_chunks(
        self,
        *,
        tenant_id: int,
        visibility: int,
        owner_user_id: int,
        teacher_user_id: int,
        raw_text: str,
        chunk_size: int = 800,
        chunk_overlap: int = 150,
    ) -> Dict[str, Any]:
        cleaned = clean_text(raw_text)
        chunks = chunk_text(cleaned, chunk_size=chunk_size, chunk_overlap=chunk_overlap)

        # chunk 文本 -> embedding
        embeddings = await self.embedding_client.embed_documents(chunks)
        coords_3d = decompose_project(
            embeddings,
            method=self.settings.DECOMP_METHOD,
            n_components=3,
            scale_to=self.settings.DECOMP_SCALE_TO,
        ) if embeddings else []

        document_id = self.snowflake.next_id()
        chunk_ids = [self.snowflake.next_id() for _ in range(len(chunks))]

        # 简单返回预览坐标（同时也用于 Milvus fallback 的内存回退）
        previews: List[Dict[str, Any]] = []

        for i, (cid, idx, txt) in enumerate(zip(chunk_ids, list(range(len(chunks))), chunks)):
            coords_i = coords_3d[i] if i < len(coords_3d) else vector_to_coords(embeddings[i], 3)
            previews.append(
                {
                    "chunkId": int(cid),
                    "chunkIndex": int(idx),
                    "textPreview": txt[:200],
                    "coords": coords_i,
                }
            )

        # 按字段顺序插入（Milvus 写入路径）
        coord_x = [float(c[0]) for c in coords_3d] if coords_3d else [float(vector_to_coords(e, 3)[0]) for e in embeddings]
        coord_y = [float(c[1]) for c in coords_3d] if coords_3d else [float(vector_to_coords(e, 3)[1]) for e in embeddings]
        coord_z = [float(c[2]) for c in coords_3d] if coords_3d else [float(vector_to_coords(e, 3)[2]) for e in embeddings]

        col = await self.ensure_collection()
        entities = {
            "id": chunk_ids,
            "tenant_id": [tenant_id] * len(chunks),
            "visibility": [visibility] * len(chunks),
            "owner_user_id": [owner_user_id] * len(chunks),
            "teacher_user_id": [teacher_user_id] * len(chunks),
            "document_id": [document_id] * len(chunks),
            "chunk_index": list(range(len(chunks))),
            "chunk_text": chunks,
            "embedding": embeddings,
            "coord_x": coord_x,
            "coord_y": coord_y,
            "coord_z": coord_z,
        }
        col.insert([
            entities["id"],
            entities["tenant_id"],
            entities["visibility"],
            entities["owner_user_id"],
            entities["teacher_user_id"],
            entities["document_id"],
            entities["chunk_index"],
            entities["chunk_text"],
            entities["embedding"],
            entities["coord_x"],
            entities["coord_y"],
            entities["coord_z"],
        ])

        return {
            "documentId": int(document_id),
            "chunkCount": len(chunks),
            "chunks": previews,
            "embedDim": len(embeddings[0]) if embeddings else 0,
            "embeddingModel": self.settings.EMBEDDING_MODEL,
        }

    def _build_expr(
        self,
        *,
        tenant_id: int,
        visibility_mode: str,
        owner_user_id: Optional[int],
    ) -> str:
        vis_public = 1
        vis_private = 0

        if visibility_mode == "PUBLIC_ONLY":
            return f"tenant_id == {tenant_id} and visibility == {vis_public}"
        if visibility_mode == "PRIVATE_ONLY":
            if owner_user_id is None:
                raise ValueError("owner_user_id is required for PRIVATE_ONLY")
            return f"tenant_id == {tenant_id} and visibility == {vis_private} and owner_user_id == {int(owner_user_id)}"
        # BOTH
        if owner_user_id is None:
            # 没有 private 条件则只返回 public
            return f"tenant_id == {tenant_id} and visibility == {vis_public}"
        return (
            f"tenant_id == {tenant_id} and ((visibility == {vis_public}) "
            f"or (visibility == {vis_private} and owner_user_id == {int(owner_user_id)}))"
        )

    async def rag_search(
        self,
        *,
        query: str,
        tenant_id: int,
        visibility_mode: str,
        owner_user_id: Optional[int],
        top_k: int = 6,
    ) -> Dict[str, Any]:
        # embedding 失败时直接返回空结果，避免整次 rag-search 直接 500
        try:
            query_vec = await self.embedding_client.embed_query(query)
        except Exception:
            return {"query": query, "topK": int(top_k), "results": []}

        # 先尝试 Milvus；Milvus 无法连接时走内存回退（保证前端可视化可运行）
        try:
            col = await self.ensure_collection()

            # 按你的约束：把 Query embedding 先写入 Milvus，再做相似度检索
            query_vector_id = self.snowflake.next_id()
            query_visibility = 1 if visibility_mode in ("PUBLIC_ONLY", "BOTH") else 0
            query_owner_user_id = int(owner_user_id) if owner_user_id is not None else 0
            try:
                col.insert(
                    [
                        [query_vector_id],  # id
                        [tenant_id],  # tenant_id
                        [query_visibility],  # visibility
                        [query_owner_user_id],  # owner_user_id
                        [0],  # teacher_user_id
                        [0],  # document_id
                        [-1],  # chunk_index
                        [""],  # chunk_text
                        [query_vec],  # embedding
                        [0.0],  # coord_x
                        [0.0],  # coord_y
                        [0.0],  # coord_z
                    ]
                )
            except Exception:
                pass

            expr = self._build_expr(
                tenant_id=tenant_id,
                visibility_mode=visibility_mode,
                owner_user_id=owner_user_id,
            )
            expr = f"({expr}) and id != {int(query_vector_id)}"

            output_fields = [
                "id",
                "document_id",
                "chunk_index",
                "chunk_text",
                "coord_x",
                "coord_y",
                "coord_z",
                "visibility",
                "owner_user_id",
                "teacher_user_id",
            ]

            search_params = {"metric_type": self.settings.MILVUS_METRIC_TYPE, "params": {"nprobe": 10}}
            results = col.search(
                data=[query_vec],
                anns_field="embedding",
                param=search_params,
                limit=int(top_k),
                expr=expr,
                output_fields=output_fields,
            )

            hits = results[0] if results else []
            best: List[Dict[str, Any]] = []

            for h in hits:
                ent = h.entity
                field_names = [
                    "id",
                    "document_id",
                    "chunk_index",
                    "chunk_text",
                    "coord_x",
                    "coord_y",
                    "coord_z",
                    "visibility",
                    "owner_user_id",
                    "teacher_user_id",
                ]
                entity_dict: Dict[str, Any] = {}
                for k in field_names:
                    try:
                        if hasattr(ent, "get") and callable(getattr(ent, "get")):
                            entity_dict[k] = ent.get(k)  # type: ignore[arg-type]
                        else:
                            entity_dict[k] = getattr(ent, k)
                    except Exception:
                        continue

                chunk_text = entity_dict.get("chunk_text") or ""
                coords = [
                    float(entity_dict.get("coord_x", 0.0) or 0.0),
                    float(entity_dict.get("coord_y", 0.0) or 0.0),
                    float(entity_dict.get("coord_z", 0.0) or 0.0),
                ]
                best.append(
                    {
                        "chunkId": int(entity_dict.get("id")),
                        "documentId": int(entity_dict.get("document_id")),
                        "chunkIndex": int(entity_dict.get("chunk_index")),
                        "text": chunk_text,
                        "score": float(getattr(h, "score", 0.0)),
                        "coords": [coords[0], coords[1], coords[2]],
                        "visibility": int(entity_dict.get("visibility", 0)),
                        "ownerUserId": entity_dict.get("owner_user_id"),
                        "teacherUserId": entity_dict.get("teacher_user_id"),
                    }
                )

            # 清理临时 query 向量
            try:
                col.delete(f"id == {int(query_vector_id)}")
            except Exception:
                pass

            return {
                "query": query,
                "topK": int(top_k),
                "results": best,
            }

        except Exception:
            # Milvus 不可达/检索失败：交给 API 层统一返回 500
            raise

