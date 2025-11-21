from __future__ import annotations

"""
Pydantic 数据模型（KISS 合并版）
保留原有字段与接口兼容性。
"""

from datetime import datetime
from typing import Any, List, Optional
from pydantic import BaseModel, Field


# === 基础通用模型 ===
class BaseResponse(BaseModel):
    code: int = Field(description="状态码")
    message: str = Field(description="响应消息")
    success: bool = Field(description="是否成功")
    data: Optional[Any] = Field(None, description="响应数据")


# === 聊天相关模型 ===
class ChatMessage(BaseModel):
    role: str = Field(description="角色: user/assistant/system")
    content: str = Field(description="消息内容")
    timestamp: Optional[datetime] = Field(None, description="时间戳")


class ChatRequest(BaseModel):
    messages: List[ChatMessage] = Field(description="消息列表")
    model: str = Field(description="模型名称: gpt-4, claude-3等")
    user_id: int = Field(description="用户ID")
    session_id: Optional[str] = Field(None, description="会话ID")
    temperature: Optional[float] = Field(0.7, ge=0, le=2, description="温度参数")
    max_tokens: Optional[int] = Field(None, description="最大token数")
    # RAG 参数
    use_rag: Optional[bool] = Field(False, description="是否启用RAG检索")
    namespace: Optional[str] = Field(None, description="RAG命名空间/租户")
    rag_top_k: Optional[int] = Field(5, description="RAG检索返回条数")
    tags: Optional[List[str]] = Field(None, description="RAG标签过滤")


class TokenUsage(BaseModel):
    prompt_tokens: int = Field(description="输入token数")
    completion_tokens: int = Field(description="输出token数")
    total_tokens: int = Field(description="总token数")


class ChatResponse(BaseModel):
    response: str = Field(description="AI回复内容")
    model: str = Field(description="使用的模型")
    usage: Optional[TokenUsage] = Field(None, description="token使用统计")
    session_id: Optional[str] = Field(None, description="会话ID")


# === RAG 相关模型 ===
class RAGRequest(BaseModel):
    query: str = Field(description="查询问题")
    user_id: int = Field(description="用户ID")
    knowledge_base: Optional[str] = Field(None, description="知识库名称")
    namespace: Optional[str] = Field(None, description="命名空间/租户")
    tags: Optional[List[str]] = Field(None, description="标签过滤")
    top_k: int = Field(5, description="返回文档数量")


class DocumentSource(BaseModel):
    title: Optional[str] = Field(None, description="文档标题")
    content: str = Field(description="文档内容")
    score: float = Field(description="相关性得分")
    metadata: Optional[dict] = Field(None, description="元数据")


class RAGIngestRequest(BaseModel):
    text: str = Field(description="原始文本内容")
    user_id: Optional[int] = Field(None, description="用户ID")
    namespace: Optional[str] = Field(None, description="命名空间/租户")
    doc_id: Optional[str] = Field(None, description="文档ID（不传则自动生成）")
    tags: Optional[List[str]] = Field(None, description="标签列表")
    metadata: Optional[dict] = Field(None, description="额外元数据")


# === Function Calling 相关模型 ===
class FunctionDefinition(BaseModel):
    name: str = Field(description="函数名称")
    description: str = Field(description="函数功能描述")
    parameters: dict = Field(description="参数schema，符合JSON Schema格式")


class FunctionCall(BaseModel):
    name: str = Field(description="调用的函数名")
    arguments: str = Field(description="函数参数JSON字符串")


class ChatMessageWithFunction(BaseModel):
    role: str = Field(description="角色: user/assistant/system/function")
    content: Optional[str] = Field(None, description="消息内容")
    function_call: Optional[FunctionCall] = Field(None, description="函数调用信息")
    name: Optional[str] = Field(None, description="函数名（当role=function时）")
    timestamp: Optional[datetime] = Field(None, description="时间戳")


class ChatRequestWithFunctions(BaseModel):
    messages: List[ChatMessageWithFunction] = Field(description="消息列表")
    model: str = Field(description="模型名称")
    user_id: int = Field(description="用户ID")
    session_id: Optional[str] = Field(None, description="会话ID")
    temperature: Optional[float] = Field(0.7, ge=0, le=2, description="温度参数")
    max_tokens: Optional[int] = Field(None, description="最大token数")
    functions: Optional[List[FunctionDefinition]] = Field(None, description="可调用的函数列表")
    function_call: Optional[str] = Field("auto", description="函数调用策略: auto, none, 或指定函数名")


class FunctionExecutionResult(BaseModel):
    function_name: str = Field(description="函数名称")
    result: Any = Field(description="执行结果")
    success: bool = Field(description="是否成功")
    error: Optional[str] = Field(None, description="错误信息")
    execution_time: Optional[float] = Field(None, description="执行时间(秒)")


class ChatResponseWithFunction(BaseModel):
    response: Optional[str] = Field(None, description="AI回复内容")
    model: str = Field(description="使用的模型")
    usage: Optional[TokenUsage] = Field(None, description="token使用统计")
    session_id: Optional[str] = Field(None, description="会话ID")
    function_call: Optional[FunctionCall] = Field(None, description="AI请求的函数调用")
    function_result: Optional[FunctionExecutionResult] = Field(None, description="函数执行结果")
    requires_function_call: bool = Field(False, description="是否需要执行函数调用")


# 兼容保留
class FunctionRequest(BaseModel):
    function_name: str = Field(description="函数名称")
    parameters: dict = Field(description="函数参数")
    user_id: int = Field(description="用户ID")


class FunctionResponse(BaseModel):
    result: Any = Field(description="执行结果")
    success: bool = Field(description="是否成功")
    error: Optional[str] = Field(None, description="错误信息")

