"""
Function 执行器与内置工具函数
"""
from __future__ import annotations

import json
import math
import os
import random
import string
import time
from typing import Any, Callable, Dict, List, Optional

import httpx
from loguru import logger

from app.models import (
    FunctionCall,
    FunctionDefinition,
    FunctionExecutionResult,
)


class FunctionExecutor:
    """Function 执行器：注册、列出、执行函数"""

    def __init__(self) -> None:
        self._handlers: Dict[str, Callable[..., Any]] = {}
        self._defs: Dict[str, FunctionDefinition] = {}

    def register(self, definition: FunctionDefinition, handler: Callable[..., Any]) -> None:
        name = definition.name
        self._handlers[name] = handler
        self._defs[name] = definition

    def list_defs_as_openai(self) -> List[dict]:
        return [
            {"name": fd.name, "description": fd.description, "parameters": fd.parameters}
            for fd in self._defs.values()
        ]

    async def execute(self, call: FunctionCall) -> FunctionExecutionResult:
        start = time.time()
        try:
            if call.name not in self._handlers:
                return FunctionExecutionResult(
                    function_name=call.name,
                    result=None,
                    success=False,
                    error=f"函数未注册: {call.name}",
                    execution_time=time.time() - start,
                )
            try:
                args = json.loads(call.arguments) if call.arguments else {}
            except Exception as e:
                return FunctionExecutionResult(
                    function_name=call.name,
                    result=None,
                    success=False,
                    error=f"参数解析失败: {e}",
                    execution_time=time.time() - start,
                )
            handler = self._handlers[call.name]
            if hasattr(handler, "__code__") and handler.__code__.co_flags & 0x80:
                result = await handler(**args)
            else:
                result = handler(**args)
            return FunctionExecutionResult(
                function_name=call.name,
                result=result,
                success=True,
                error=None,
                execution_time=time.time() - start,
            )
        except Exception as e:
            return FunctionExecutionResult(
                function_name=call.name,
                result=None,
                success=False,
                error=str(e),
                execution_time=time.time() - start,
            )


# 全局单例
function_executor = FunctionExecutor()


def register_function(name: str, description: str, parameters: dict):
    """装饰器：注册函数到执行器"""

    def decorator(func: Callable[..., Any]):
        function_executor.register(
            FunctionDefinition(name=name, description=description, parameters=parameters),
            func,
        )
        return func

    return decorator


# ===================== 内置工具函数 =====================


@register_function(
    name="get_current_time",
    description="获取当前时间信息",
    parameters={
        "type": "object",
        "properties": {
            "timezone": {"type": "string", "enum": ["Asia/Shanghai", "UTC", "America/New_York"]},
            "format": {"type": "string", "default": "%Y-%m-%d %H:%M:%S"},
        },
        "required": [],
    },
)
def get_current_time(timezone: str = "Asia/Shanghai", format: str = "%Y-%m-%d %H:%M:%S") -> dict:
    from datetime import datetime

    try:
        from zoneinfo import ZoneInfo
    except ImportError:
        from backports.zoneinfo import ZoneInfo  # type: ignore

    tz = ZoneInfo(timezone)
    now = datetime.now(tz)
    return {
        "current_time": now.strftime(format),
        "timezone": timezone,
        "timestamp": int(now.timestamp()),
        "day_of_week": now.strftime("%A"),
        "is_weekend": now.weekday() >= 5,
    }


@register_function(
    name="calculate",
    description="执行数学计算，支持基本算术和常见函数",
    parameters={
        "type": "object",
        "properties": {
            "expression": {"type": "string"},
            "precision": {"type": "integer", "default": 2},
        },
        "required": ["expression"],
    },
)
def calculate(expression: str, precision: int = 2) -> dict:
    # 安全检查
    if any(w in expression.lower() for w in ["import", "exec", "eval", "__", "open", "file"]):
        return {"error": "不安全的表达式"}

    allowed = {
        "abs": abs,
        "round": round,
        "min": min,
        "max": max,
        "sum": sum,
        "pow": pow,
        "sqrt": math.sqrt,
        "sin": math.sin,
        "cos": math.cos,
        "tan": math.tan,
        "log": math.log,
        "log10": math.log10,
        "pi": math.pi,
        "e": math.e,
    }
    try:
        val = eval(expression, {"__builtins__": {}}, allowed)
        if isinstance(val, float):
            val = round(val, precision)
        return {"expression": expression, "result": val, "type": type(val).__name__}
    except Exception as e:
        return {"error": f"计算错误: {e}"}


@register_function(
    name="generate_random_password",
    description="生成随机密码",
    parameters={
        "type": "object",
        "properties": {
            "length": {"type": "integer", "default": 12, "minimum": 6, "maximum": 50},
            "include_symbols": {"type": "boolean", "default": True},
            "include_numbers": {"type": "boolean", "default": True},
        },
        "required": [],
    },
)
def generate_random_password(
    length: int = 12, include_symbols: bool = True, include_numbers: bool = True
) -> dict:
    chars = string.ascii_letters
    if include_numbers:
        chars += string.digits
    if include_symbols:
        chars += "!@#$%^&*()_+-=[]{}|;:,.<>?"
    pwd = "".join(random.choice(chars) for _ in range(length))
    return {"password": pwd, "length": len(pwd)}


@register_function(
    name="check_website_status",
    description="检查网站状态和响应时间",
    parameters={
        "type": "object",
        "properties": {
            "url": {"type": "string", "format": "uri"},
            "timeout": {"type": "integer", "default": 10},
        },
        "required": ["url"],
    },
)
async def check_website_status(url: str, timeout: int = 10) -> dict:
    start = time.time()
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.head(url)
        ms = round((time.time() - start) * 1000, 2)
        return {
            "url": url,
            "status_code": resp.status_code,
            "status": "online" if resp.status_code < 400 else "error",
            "response_time": ms,
        }
    except Exception as e:
        return {"url": url, "status": "offline", "error": str(e)}


@register_function(
    name="web_search",
    description="搜索网络信息，支持多种搜索引擎",
    parameters={
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "搜索关键词"},
            "limit": {"type": "integer", "default": 3, "minimum": 1, "maximum": 10},
            "engines": {
                "type": "array",
                "items": {"type": "string"},
                "default": ["bing"],
            },
        },
        "required": ["query"],
    },
)
async def web_search(query: str, limit: int = 3, engines: Optional[List[str]] = None) -> dict:
    """通过本地 open-websearch-mcp 服务器执行网络搜索"""
    search_enabled = os.getenv("WEB_SEARCH_ENABLED", "true").lower() == "true"
    if not search_enabled:
        return {"error": "网络搜索功能未启用，请设置 WEB_SEARCH_ENABLED=true"}

    engines = ["bing"]
    logger.info(f"启动 MCP 搜索: query='{query}', engines={engines}, limit={limit}")

    try:
        from mcp import ClientSession, StdioServerParameters
        from mcp.client import stdio

        server_params = StdioServerParameters(
            command="npx",
            args=["-y", "open-websearch@latest"],
            env=None,
        )

        try:
            async with stdio.stdio_client(server_params) as (read, write):
                async with ClientSession(read, write) as session:
                    await session.initialize()
                    result = await session.call_tool(
                        "search",
                        {"query": query, "limit": limit, "engines": engines},
                    )

                    if hasattr(result, "content"):
                        content = result.content
                        if isinstance(content, list) and len(content) > 0:
                            first_content = content[0]
                            if hasattr(first_content, "text"):
                                search_data = json.loads(first_content.text)
                                raw_results = search_data.get("results", [])
                                formatted_results = [
                                    {
                                        "title": item.get("title", ""),
                                        "url": item.get("url", ""),
                                        "snippet": item.get("description", item.get("snippet", "")),
                                        "engine": item.get("engine", "unknown"),
                                    }
                                    for item in raw_results
                                    if isinstance(item, dict)
                                ]
                                return {"results": formatted_results, "query": query, "engines": engines}
                    return {"results": [], "query": query, "engines": engines}

        except Exception as mcp_error:
            logger.error(f"MCP 会话错误: {mcp_error}")
            return {"error": f"MCP 连接错误: {str(mcp_error)}"}

    except ImportError as e:
        logger.error(f"MCP SDK 未安装: {e}")
        return {"error": "MCP SDK 未安装，请运行: pip install mcp"}
    except Exception as e:
        logger.error(f"网络搜索失败: {e}")
        return {"error": f"搜索失败: {str(e)}"}
