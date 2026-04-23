import os
import sys
from pathlib import Path
from typing import List

# 允许 PyCharm / 命令行直接运行本文件：python app/main.py（非包上下文时补上项目根目录）
if __package__ is None:
    _root = Path(__file__).resolve().parent.parent
    if str(_root) not in sys.path:
        sys.path.insert(0, str(_root))

from dotenv import load_dotenv

# 必须先加载 .env，再 import ai_routes（否则会先执行 Settings()，读不到 QWEN_API_KEY）
load_dotenv()

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.ai_routes import router as ai_router


def parse_origins() -> List[str]:
    raw = os.getenv("CORS_ALLOW_ORIGINS", "*").strip()
    if raw == "*":
        return ["*"]
    return [x.strip() for x in raw.split(",") if x.strip()]


def create_app() -> FastAPI:
    app = FastAPI(
        title="AI Decomposer - AI Gateway",
        version="0.1.0",
    )

    # CORS
    app.add_middleware(
        CORSMiddleware,
        allow_origins=parse_origins(),
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Router
    app.include_router(ai_router)

    @app.get("/health")
    async def health():
        return {"ok": True}

    # Exceptions
    @app.exception_handler(HTTPException)
    async def http_exception_handler(request: Request, exc: HTTPException):
        return JSONResponse(
            status_code=exc.status_code,
            content={"code": "HTTP_ERROR", "message": exc.detail},
        )

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(request: Request, exc: Exception):
        # 生产环境建议记录日志到 Kafka/ELK
        return JSONResponse(
            status_code=500,
            content={"code": "INTERNAL_ERROR", "message": str(exc)},
        )

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("PORT", "8000")),
    )

