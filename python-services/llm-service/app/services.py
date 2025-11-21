"""
服务入口（兼容层）
重新导出拆分后的模块，保持向后兼容
"""
from app.functions import (
    FunctionExecutor,
    function_executor,
    register_function,
    get_current_time,
    calculate,
    generate_random_password,
    check_website_status,
    web_search,
)

from app.rag_service import (
    RAGService,
    get_rag_service,
)

from app.llm_service import (
    LLMService,
    get_llm_service,
)

__all__ = [
    # Functions
    "FunctionExecutor",
    "function_executor",
    "register_function",
    "get_current_time",
    "calculate",
    "generate_random_password",
    "check_website_status",
    "web_search",
    # RAG
    "RAGService",
    "get_rag_service",
    # LLM
    "LLMService",
    "get_llm_service",
]
