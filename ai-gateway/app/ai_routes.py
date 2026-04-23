from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any, Dict, Literal, Optional

if __package__ is None:
    _root = Path(__file__).resolve().parent.parent
    if str(_root) not in sys.path:
        sys.path.insert(0, str(_root))

from dotenv import load_dotenv

# 被测试或其它入口直接 import 本模块时，也保证先读 .env
load_dotenv()

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, ConfigDict

from app.ai_services import EmbeddingClient, MilvusRagRepository, QwenStreamingClient, Settings
from app.snowflake import SnowflakeIdGenerator


router = APIRouter(prefix="/api/ai", tags=["AI"])


class PromptTestRequest(BaseModel):
    prompt: str = Field(..., min_length=1, max_length=60000)
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    top_p: float = Field(default=0.9, ge=0.0, le=1.0)


class RagSearchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str = Field(..., min_length=1, max_length=20000)
    tenant_id: int = Field(..., ge=0, alias="tenantId")
    visibility_mode: Literal["PUBLIC_ONLY", "PRIVATE_ONLY", "BOTH"] = Field("BOTH", alias="visibilityMode")
    owner_user_id: Optional[int] = Field(None, ge=0, alias="ownerUserId")
    top_k: int = Field(default=6, ge=1, le=50, alias="topK")


class TokenProbabilityTreeRequest(BaseModel):
    prompt: str = Field(..., min_length=1, max_length=60000)
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    top_p: float = Field(default=0.9, ge=0.0, le=1.0)


class PromptAbEvalRequest(BaseModel):
    """A/B 输出自我评测：由 Qwen（默认 qwen3-max）裁判打分。"""

    model_config = ConfigDict(populate_by_name=True)

    user_task: str = Field(..., min_length=1, max_length=60000, alias="userTask")
    output_a: str = Field(default="", max_length=80000, alias="outputA")
    output_b: str = Field(default="", max_length=80000, alias="outputB")
    params_a: Optional[Dict[str, Any]] = Field(default=None, alias="paramsA")
    params_b: Optional[Dict[str, Any]] = Field(default=None, alias="paramsB")


settings = Settings()
snowflake = SnowflakeIdGenerator(
    worker_id=settings.SNOWFLAKE_WORKER_ID,
    datacenter_id=settings.SNOWFLAKE_DATACENTER_ID,
)
qwen_client = QwenStreamingClient(settings)
embedding_client = EmbeddingClient(settings)
milvus_repo = MilvusRagRepository(settings, embedding_client, snowflake)


@router.post("/prompt-test")
async def prompt_test(req: PromptTestRequest):
    """
    接收 prompt、temperature、top_p，调用 Qwen 模型并返回流式结果（SSE）。
    """
    if not req.prompt.strip():
        raise HTTPException(status_code=400, detail="prompt must not be empty")

    async def sse_event_gen():
        try:
            async for token in qwen_client.stream_prompt(
                prompt=req.prompt,
                temperature=req.temperature,
                top_p=req.top_p,
            ):
                # 前端可用 eventSource 或自定义 SSE 解析
                payload = json.dumps({"token": token}, ensure_ascii=False)
                yield f"data: {payload}\n\n"
            yield "data: " + json.dumps({"done": True}, ensure_ascii=False) + "\n\n"
        except Exception as e:
            payload = json.dumps({"error": str(e)}, ensure_ascii=False)
            yield f"event: error\ndata: {payload}\n\n"

    # 说明：如果你们前端不是按 SSE 解析，可把 media_type 改成 text/plain 并 yield token
    return StreamingResponse(
        sse_event_gen(),
        media_type="text/event-stream; charset=utf-8",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "Connection": "keep-alive",
            # 常见反向代理（如 Nginx）默认会缓冲 SSE，导致前端长时间看不到增量
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/document-process")
async def document_process(
    file: UploadFile = File(...),
    tenant_id: int = Form(..., ge=0),
    visibility: Literal["PUBLIC", "PRIVATE"] = Form("PRIVATE"),
    owner_user_id: int = Form(..., ge=0),
    teacher_user_id: Optional[int] = Form(None),
    chunk_size: int = Form(800, ge=200, le=2000),
    chunk_overlap: int = Form(150, ge=0, le=600),
):
    """
    接收文本文件，执行：
    文本清洗 -> Chunking -> Embedding -> 写入 Milvus
    """
    if not file:
        raise HTTPException(status_code=400, detail="file is required")

    visibility_int = 1 if visibility == "PUBLIC" else 0
    if visibility_int == 1:
        teacher_user_id_val = int(teacher_user_id) if teacher_user_id is not None else int(owner_user_id)
    else:
        teacher_user_id_val = int(teacher_user_id) if teacher_user_id is not None else 0

    raw_bytes = await file.read()
    if not raw_bytes:
        raise HTTPException(status_code=400, detail="uploaded file is empty")

    # 尽量按 utf-8 解码；若失败则忽略非法字符
    try:
        raw_text = raw_bytes.decode("utf-8", errors="ignore")
    except Exception:
        raise HTTPException(status_code=400, detail="file decoding failed, please upload text/plain utf-8")

    if not raw_text.strip():
        raise HTTPException(status_code=400, detail="decoded text is empty")

    try:
        result = await milvus_repo.upsert_document_chunks(
            tenant_id=int(tenant_id),
            visibility=visibility_int,
            owner_user_id=int(owner_user_id),
            teacher_user_id=teacher_user_id_val,
            raw_text=raw_text,
            chunk_size=int(chunk_size),
            chunk_overlap=int(chunk_overlap),
        )
        return result
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"document processing failed: {e}")


@router.post("/rag-search")
async def rag_search(req: RagSearchRequest):
    """
    接收用户 Query：
    - Query -> Embedding
    - 在 Milvus 里做相似度检索 Top-K
    - 返回最相关 chunk 的文本与向量坐标（coords）
    """
    if req.visibility_mode == "PRIVATE_ONLY" and req.owner_user_id is None:
        raise HTTPException(status_code=400, detail="owner_user_id is required for PRIVATE_ONLY")

    try:
        return await milvus_repo.rag_search(
            query=req.query,
            tenant_id=int(req.tenant_id),
            visibility_mode=req.visibility_mode,
            owner_user_id=req.owner_user_id,
            top_k=int(req.top_k),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        # 重要：把完整堆栈打到日志里，便于我们定位 MilvusException 到底是哪一步触发
        import traceback

        traceback.print_exc()
        # 返回异常类型+repr，避免 message 为空导致排障困难
        raise HTTPException(status_code=500, detail=f"rag search failed: {type(e).__name__}: {e!r}")


@router.post("/prompt-ab-eval")
async def prompt_ab_eval(req: PromptAbEvalRequest):
    """
    Prompt 实验室 A/B：对两份模型输出做准确性、简洁度对比评分（JSON）。
    """
    try:
        return await qwen_client.evaluate_prompt_ab_outputs(
            user_task=req.user_task,
            output_a=req.output_a,
            output_b=req.output_b,
            params_a=req.params_a,
            params_b=req.params_b,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"prompt ab eval failed: {e}") from e


@router.post("/token-probability-tree")
async def token_probability_tree(req: TokenProbabilityTreeRequest):
    if not req.prompt.strip():
        raise HTTPException(status_code=400, detail="prompt must not be empty")

    try:
        return await qwen_client.generate_probability_tree(
            prompt=req.prompt,
            temperature=req.temperature,
            top_p=req.top_p,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"token probability tree failed: {e}")


if __name__ == "__main__":
    import os

    import uvicorn

    uvicorn.run(
        "app.main:app",
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("PORT", "8000")),
    )

