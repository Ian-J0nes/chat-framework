"""
LLM 服务：OpenAI API 调用、模型管理、Function Calling
"""
from __future__ import annotations

import json
import os
import re
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from dotenv import load_dotenv
from loguru import logger
from openai import AsyncOpenAI

from app.functions import function_executor
from app.models import (
    ChatMessage,
    ChatRequest,
    ChatRequestWithFunctions,
    ChatResponse,
    ChatResponseWithFunction,
    FunctionCall,
    TokenUsage,
)
from app.rag_service import get_rag_service

# 自动加载 .env
try:
    _ENV_PATH = Path(__file__).resolve().parents[1] / ".env"
    load_dotenv(dotenv_path=_ENV_PATH, override=False)
except Exception as e:
    logger.warning(f".env load skipped: {e}")


class LLMService:
    """LLM 服务：聊天完成、Function Calling、模型管理"""

    def __init__(self) -> None:
        self.openai_api_key = os.getenv("OPENAI_API_KEY")
        self.openai_base_url = os.getenv("OPENAI_BASE_URL")
        self.rag_default_on = os.getenv("RAG_DEFAULT_ON", "false").lower() == "true"
        self.rag_default_namespace = os.getenv("RAG_DEFAULT_NAMESPACE")

        self.openai_client = (
            AsyncOpenAI(api_key=self.openai_api_key, base_url=self.openai_base_url)
            if self.openai_api_key
            else None
        )

        # 支持的模型（作为回退）
        env_models = (os.getenv("LLM_SUPPORTED_MODELS") or "").strip()
        fallback_list = [m.strip() for m in env_models.split(",") if m.strip()] or ["gpt-4.1-nano"]
        self.supported_models = {m: "supported" for m in fallback_list}

        # 模型过滤正则
        allowed_regex = (os.getenv("LLM_ALLOWED_MODEL_REGEX") or "gpt").strip() or "gpt"
        try:
            self._allowed_model_re = re.compile(allowed_regex, re.IGNORECASE)
            self._allowed_model_regex = allowed_regex
        except Exception as e:
            logger.warning(f"LLM_ALLOWED_MODEL_REGEX 无效('{allowed_regex}')，回退为 'gpt': {e}")
            self._allowed_model_re = re.compile("gpt", re.IGNORECASE)
            self._allowed_model_regex = "gpt"

        logger.info(f"LLMService 初始化完成，allowed_regex=/{self._allowed_model_regex}/i")

    async def chat_completion(self, request: ChatRequest) -> ChatResponse:
        """执行聊天完成"""
        if not await self.is_model_supported(request.model):
            raise ValueError(f"不支持的模型: {request.model}")
        if not self.openai_client:
            raise ValueError("OpenAI API Key 未配置，无法调用LLM")

        # 可选 RAG 注入
        try:
            request.messages = await self._maybe_inject_rag_context(request)
        except Exception as e:
            logger.warning(f"RAG 注入失败，回退: {e}")

        text, usage = await self._call_openai_api(request)
        return ChatResponse(response=text, model=request.model, usage=usage, session_id=request.session_id)

    async def _call_openai_api(self, request: ChatRequest) -> Tuple[str, TokenUsage]:
        """调用 OpenAI API"""
        messages = [{"role": m.role, "content": m.content} for m in request.messages]
        resp = await self.openai_client.chat.completions.create(
            model=request.model,
            messages=messages,
            temperature=request.temperature or 0.7,
            max_tokens=request.max_tokens or 8192,
        )
        content = resp.choices[0].message.content
        usage = TokenUsage(
            prompt_tokens=resp.usage.prompt_tokens,
            completion_tokens=resp.usage.completion_tokens,
            total_tokens=resp.usage.total_tokens,
        )
        return content, usage

    async def get_available_models(self) -> List[dict]:
        """返回可用模型列表（经过正则过滤）"""
        if not self.openai_client:
            base = [
                {"id": mid, "object": "model", "created": 0, "owned_by": "fallback"}
                for mid in self.supported_models.keys()
            ]
            return [m for m in base if isinstance(m.get("id"), str) and self._allowed_model_re.search(m["id"])]

        try:
            models = await self.openai_client.models.list()
            out: List[dict] = []
            for m in models.data:
                item = {
                    "id": getattr(m, "id", None),
                    "object": getattr(m, "object", "model"),
                    "created": getattr(m, "created", 0),
                    "owned_by": getattr(m, "owned_by", "unknown"),
                }
                try:
                    supp = getattr(m, "supported_endpoint_types", None)
                    if supp is not None:
                        item["supported_endpoint_types"] = supp
                except Exception:
                    pass
                out.append(item)

            filtered = [
                m for m in out if isinstance(m.get("id"), str) and self._allowed_model_re.search(str(m["id"]))
            ]
            if filtered:
                return filtered

            # 回退到本地 supported_models
            fallback = [
                {"id": mid, "object": "model", "created": 0, "owned_by": "fallback"}
                for mid in self.supported_models.keys()
            ]
            return [m for m in fallback if self._allowed_model_re.search(m["id"])]

        except Exception as e:
            logger.warning(f"获取模型失败: {e}")
            base = [
                {"id": mid, "object": "model", "created": 0, "owned_by": "fallback"}
                for mid in self.supported_models.keys()
            ]
            return [m for m in base if self._allowed_model_re.search(m["id"])]

    def get_available_functions(self) -> List[dict]:
        """返回可用函数列表"""
        return function_executor.list_defs_as_openai()

    async def chat_with_functions(self, request: ChatRequestWithFunctions) -> ChatResponseWithFunction:
        """带函数调用的聊天"""
        if not await self.is_model_supported(request.model):
            raise ValueError(f"不支持的模型: {request.model}")
        if not self.openai_client:
            raise ValueError("OpenAI API Key 未配置，无法调用LLM")

        msgs: List[dict] = []
        for msg in request.messages:
            d = {"role": msg.role, "content": msg.content or ""}
            if msg.function_call:
                d["function_call"] = {"name": msg.function_call.name, "arguments": msg.function_call.arguments}
            if msg.name:
                d["name"] = msg.name
            msgs.append(d)

        params: Dict[str, Any] = {
            "model": request.model,
            "messages": msgs,
            "temperature": request.temperature or 0.7,
            "max_tokens": request.max_tokens or 1000,
        }

        funcs = request.functions or self.get_available_functions()
        if funcs:
            params["functions"] = funcs
            params["function_call"] = request.function_call or "auto"

        resp = await self.openai_client.chat.completions.create(**params)
        message = resp.choices[0].message
        usage = TokenUsage(
            prompt_tokens=resp.usage.prompt_tokens,
            completion_tokens=resp.usage.completion_tokens,
            total_tokens=resp.usage.total_tokens,
        )

        if message.function_call:
            call = FunctionCall(name=message.function_call.name, arguments=message.function_call.arguments)
            result = await function_executor.execute(call)

            # 让模型基于结果生成最终回复
            follow_msgs: List[dict] = []
            for m in request.messages:
                follow_msgs.append({"role": m.role, "content": m.content or ""})
            follow_msgs.append({
                "role": "assistant",
                "content": "",
                "function_call": {"name": call.name, "arguments": call.arguments},
            })
            follow_msgs.append({
                "role": "function",
                "name": call.name,
                "content": json.dumps(result.result, ensure_ascii=False),
            })

            resp2 = await self.openai_client.chat.completions.create(
                model=request.model,
                messages=follow_msgs,
                temperature=request.temperature or 0.7,
                max_tokens=request.max_tokens or 1000,
            )

            final_usage = TokenUsage(
                prompt_tokens=usage.prompt_tokens + resp2.usage.prompt_tokens,
                completion_tokens=usage.completion_tokens + resp2.usage.completion_tokens,
                total_tokens=usage.total_tokens + resp2.usage.total_tokens,
            )

            return ChatResponseWithFunction(
                response=resp2.choices[0].message.content or "处理完成",
                model=request.model,
                usage=final_usage,
                session_id=request.session_id,
                function_call=call,
                function_result=result,
                requires_function_call=False,
            )
        else:
            return ChatResponseWithFunction(
                response=message.content or "",
                model=request.model,
                usage=usage,
                session_id=request.session_id,
                function_call=None,
                function_result=None,
                requires_function_call=False,
            )

    async def is_model_supported(self, model: str) -> bool:
        """检查模型是否支持"""
        try:
            models = await self.get_available_models()
            ids = [m["id"] for m in models]
            return model in ids
        except Exception:
            return model in self.supported_models

    async def _maybe_inject_rag_context(self, request: ChatRequest) -> List[ChatMessage]:
        """可选的 RAG 上下文注入"""
        effective = bool(getattr(request, "use_rag", False) or getattr(self, "rag_default_on", False))
        if not effective or not request.messages:
            return request.messages

        rag = get_rag_service()
        q = request.messages[-1].content
        ns = request.namespace or self.rag_default_namespace
        top_k = request.rag_top_k or 5

        res = await rag.query(query=q, top_k=top_k, namespace=ns, user_id=request.user_id, tags=request.tags)

        snippets: List[str] = []
        for i, item in enumerate(res.get("results", [])[:top_k]):
            meta = item.get("metadata") or {}
            snippets.append(
                f"[{i+1}] ns={meta.get('namespace','')}, doc={meta.get('doc_id','')}, idx={meta.get('chunk_index','')}\n{item.get('content','')}"
            )

        if not snippets:
            return request.messages

        sys = ChatMessage(
            role="system",
            content=(
                "你是检索增强助手。以下是参考内容（[] 编号）。优先依据参考内容回答；不确定时说明不确定。\n"
                + "\n\n".join(snippets)
            ),
        )
        return [sys] + request.messages


@lru_cache(maxsize=1)
def get_llm_service() -> LLMService:
    """获取 LLM 服务单例"""
    return LLMService()
