from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from loguru import logger
from pathlib import Path
from dotenv import load_dotenv
import os
import asyncio
from typing import Optional

from app.models import (
    BaseResponse,
    ChatRequest,
    ChatResponse,
    ChatRequestWithFunctions,
    ChatResponseWithFunction,
    RAGIngestRequest,
    RAGRequest,
)
from app.services import get_llm_service, get_rag_service, LLMService


app = FastAPI(title="LLM Service API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

"""在 main 中直接声明所有路由，保持原有路径与返回结构。"""

# 自动加载 .env（服务根目录）
try:
    _ENV_PATH = Path(__file__).resolve().parents[1] / ".env"
    _ENV_LOADED = load_dotenv(dotenv_path=_ENV_PATH, override=False)
except Exception as e:
    logger.warning(f"[startup] .env load skipped: {e}")


@app.post("/api/llm/chat", response_model=BaseResponse)
async def chat_completion(request: ChatRequest, llm: LLMService = Depends(get_llm_service)):
    try:
        resp: ChatResponse = await llm.chat_completion(request)
        return BaseResponse(code=200, message="success", success=True, data=resp)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"聊天服务异常: {e}")


@app.get("/api/llm/models", response_model=BaseResponse)
async def get_models(llm: LLMService = Depends(get_llm_service)):
    try:
        logger.info("[/api/llm/models] request received")
        models = await llm.get_available_models()
        logger.info(f"[/api/llm/models] returning {len(models)} models")
        return BaseResponse(code=200, message="success", success=True, data={"models": [m["id"] for m in models], "models_detail": models, "count": len(models)})
    except Exception as e:
        logger.warning(f"[/api/llm/models] failed: {e}")
        raise HTTPException(status_code=500, detail=f"获取模型列表失败: {e}")


@app.get("/api/llm/status", response_model=BaseResponse)
async def chat_status():
    return BaseResponse(code=200, message="success", success=True, data={"status": "running", "service": "chat", "features": ["multi-model", "function-calling", "rag-optional"]})


@app.post("/api/llm/chat-with-functions", response_model=BaseResponse)
async def chat_with_functions(request: ChatRequestWithFunctions, llm: LLMService = Depends(get_llm_service)):
    try:
        resp: ChatResponseWithFunction = await llm.chat_with_functions(request)
        return BaseResponse(code=200, message="success", success=True, data=resp)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Function Calling 聊天服务异常: {e}")


@app.get("/api/llm/functions", response_model=BaseResponse)
async def get_functions(llm: LLMService = Depends(get_llm_service)):
    try:
        funcs = llm.get_available_functions()
        return BaseResponse(code=200, message="success", success=True, data={"functions": funcs, "count": len(funcs)})
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"获取函数列表失败: {e}")


@app.post("/api/rag/ingest", response_model=BaseResponse)
async def rag_ingest(request: RAGIngestRequest):
    try:
        rag = get_rag_service()
        result = await rag.ingest_text(text=request.text, doc_id=request.doc_id, namespace=request.namespace, user_id=request.user_id, tags=request.tags, extra_metadata=request.metadata)
        return BaseResponse(code=200, message="success", success=True, data=result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"RAG 写入失败: {e}")


@app.post("/api/rag/query", response_model=BaseResponse)
async def rag_query(request: RAGRequest):
    try:
        rag = get_rag_service()
        result = await rag.query(query=request.query, top_k=request.top_k or 5, namespace=request.namespace, user_id=request.user_id, tags=request.tags)
        return BaseResponse(code=200, message="success", success=True, data=result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"RAG 检索失败: {e}")


@app.on_event("startup")
async def _startup_warmup():
    logger.info("[startup] Initializing services...")

    # LLM Service
    try:
        llm = get_llm_service()
        if getattr(llm, "openai_client", None) is not None:
            logger.info(f"[startup] ✅ LLM Service ready")
        else:
            logger.warning("[startup] ⚠️ LLM disabled (no API key)")
    except Exception as e:
        logger.warning(f"[startup] LLM init failed: {e}")

    # RAG Service
    try:
        rag = get_rag_service()
        has_emb = bool(getattr(rag, "_emb_client", None))
        has_chroma = bool(getattr(rag, "_rest_base_url", None))

        if has_emb and has_chroma:
            logger.info(f"[startup] ✅ RAG Service ready (embeddings + vector db)")
        elif has_emb:
            logger.info(f"[startup] ✅ RAG Service ready (embeddings only)")
        else:
            logger.info(f"[startup] ⚠️ RAG disabled (no embeddings)")
    except Exception as e:
        logger.warning(f"[startup] RAG init failed: {e}")

    # Optional embedded worker
    try:
        if os.getenv("RUN_WORKER", "0") == "1":
            from app.worker_mq import run_consumer as _run_worker
            global _WORKER_TASK
            _WORKER_TASK = asyncio.create_task(_run_worker())
            logger.info("[startup] ✅ Embedded Worker started")
    except Exception as e:
        logger.warning(f"[startup] Worker start failed: {e}")

    logger.info("[startup] 🚀 LLM Service ready")


@app.on_event("shutdown")
async def _shutdown_cleanup():
    # Gracefully stop embedded worker if running
    try:
        global _WORKER_TASK
        task = globals().get("_WORKER_TASK")
        if task is not None:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
            logger.info("[shutdown] MQ worker stopped")
    except Exception as e:
        logger.warning(f"[shutdown] worker stop note: {e}")


@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "llm-service", "version": "1.0.0"}


@app.get("/")
async def root():
    return {"message": "LLM Service API", "docs_url": "/docs", "health_url": "/health"}


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    raise HTTPException(status_code=500, detail=str(exc))


if __name__ == "__main__":
    # 使用环境变量 LLM_SERVICE_PORT，默认18080
    port = int(os.getenv("LLM_SERVICE_PORT", "18080"))
    uvicorn.run("app.main:app", host="0.0.0.0", port=port, reload=False, log_level="info")
