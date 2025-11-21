"""
RabbitMQ Consumer Worker for async chat generation.

KISS 实现：
- 订阅 chat.generate → 调用 LLM → 将助手消息写入 data-service → 可选更新 Token 用量
- 失败分类：临时错误投递到 retry 队列（TTL 后回流），不可恢复错误投递到 DLQ

环境变量：
- RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USERNAME, RABBITMQ_PASSWORD, RABBITMQ_VHOST
- MQ_EXCHANGE (default: chat.x)
- MQ_ROUTING_GENERATE (default: chat.generate)
- MQ_ROUTING_RETRY (default: chat.generate.retry)
 - MQ_ROUTING_DLQ (default: chat.generate.dlq)
 - MQ_ROUTING_GENERATED (default: chat.generated)
 - MQ_ROUTING_GENERATED_RETRY (default: chat.generated.retry)
 - MQ_ROUTING_GENERATED_DLQ (default: chat.generated.dlq)

启动：
- python -m app.worker_mq
"""

# NOTE: This worker publishes 'chat.generated' events and does not perform HTTP writeback.
# Data persistence and token usage accumulation are handled by data-service's AMQP listener.
from __future__ import annotations

import asyncio
import json
import os
from dataclasses import dataclass
from typing import Any, Dict, Optional, List

import aio_pika
from loguru import logger

from app.models import (
    ChatMessage as MsgModel,
    ChatRequest as ChatReqModel,
    ChatMessageWithFunction as MsgFunc,
    ChatRequestWithFunctions as ChatReqFunc,
)
from app.services import get_llm_service, LLMService


@dataclass
class GenerateTask:
    """消息任务载荷

    入参字段：
    - request_id: 幂等请求ID（必填）
    - session_id: 业务会话ID（必填）
    - user_id: 用户ID（必填）
    - model: 模型名（必填）
    - last_user_message: 用户最新消息内容（必填）
    - use_rag/namespace/tags: 可选
    """

    request_id: str
    session_id: str
    user_id: int
    model: str
    last_user_message: str
    history: Optional[List[Dict[str, Any]]] = None
    use_rag: Optional[bool] = None
    namespace: Optional[str] = None
    tags: Optional[list[str]] = None

    @staticmethod
    def from_json(data: Dict[str, Any]) -> "GenerateTask":
        return GenerateTask(
            request_id=str(data.get("request_id") or ""),
            session_id=str(data.get("session_id") or ""),
            user_id=int(data.get("user_id")),
            model=str(data.get("model") or ""),
            last_user_message=str(data.get("last_user_message") or ""),
            history=(data.get("history") if isinstance(data.get("history"), list) else None),
            use_rag=data.get("use_rag"),
            namespace=data.get("namespace"),
            tags=data.get("tags"),
        )


def _env(key: str, default: Optional[str] = None) -> str:
    v = os.getenv(key)
    return v if v is not None else (default or "")


EXCHANGE = _env("MQ_EXCHANGE", "chat.x")
ROUTING_GENERATE = _env("MQ_ROUTING_GENERATE", "chat.generate")
ROUTING_RETRY = _env("MQ_ROUTING_RETRY", "chat.generate.retry")
ROUTING_DLQ = _env("MQ_ROUTING_DLQ", "chat.generate.dlq")
ROUTING_GENERATED = _env("MQ_ROUTING_GENERATED", "chat.generated")
ROUTING_GENERATED_RETRY = _env("MQ_ROUTING_GENERATED_RETRY", "chat.generated.retry")
ROUTING_GENERATED_DLQ = _env("MQ_ROUTING_GENERATED_DLQ", "chat.generated.dlq")



async def _post_json(url: str, payload: Dict[str, Any], timeout: float = 15.0) -> None:
    """简单 POST JSON（入参：url/payload；出参：无，失败抛出异常）"""
    return None


async def _handle_message(body: bytes, llm: LLMService, channel: aio_pika.Channel) -> None:
    data = json.loads(body.decode("utf-8"))
    task = GenerateTask.from_json(data)

    logger.info(f"[worker] 开始处理消息: request_id={task.request_id}, session_id={task.session_id}, user_id={task.user_id}")

    # 基本校验
    if not (task.request_id and task.session_id and task.user_id and task.model and task.last_user_message):
        raise ValueError("invalid payload: missing required fields")

    logger.info(f"[worker] 用户消息: {task.last_user_message[:100]}...")

    # 1) 构建消息（优先使用 history），并默认启用 Function Calling
    msgs: List[MsgModel]
    if task.history:
        logger.info(f"[worker] 使用历史消息，共 {len(task.history)} 条")
        msgs = []
        for it in task.history:
            try:
                role = str((it or {}).get("role") or "").strip()
                content = str((it or {}).get("content") or "")
                if role and content:
                    msgs.append(MsgModel(role=role, content=content))
            except Exception:
                continue
        if not msgs:
            msgs = [MsgModel(role="user", content=task.last_user_message)]
    else:
        logger.info("[worker] 使用单条最新消息")
        msgs = [MsgModel(role="user", content=task.last_user_message)]

    logger.info(f"[worker] 准备调用 LLM，消息数量: {len(msgs)}")

    # 默认改为使用工具：将消息映射为 ChatMessageWithFunction 并调用 chat_with_functions
    msgs_func: List[MsgFunc] = [MsgFunc(role=m.role, content=m.content) for m in msgs]
    req_func = ChatReqFunc(
        messages=msgs_func,
        model=task.model,
        user_id=task.user_id,
        session_id=task.session_id,
        # functions 留空，由服务内自动提供已注册函数列表
    )

    logger.info("[worker] 开始调用 LLM...")
    resp = await llm.chat_with_functions(req_func)
    logger.info(f"[worker] LLM 调用完成，响应长度: {len(resp.response) if resp.response else 0}")

    try:
        fc = getattr(resp, "function_call", None)
        if fc:
            logger.info(f"[worker] function_call used name={getattr(fc, 'name', '')}")
        else:
            logger.info("[worker] no function_call used (model answered directly)")
    except Exception:
        pass

    logger.info("[worker] 准备发布 chat.generated 事件...")

    # 2) 发布生成结果事件（双向 MQ）
    gen_payload: Dict[str, Any] = {
        "request_id": task.request_id,
        "session_id": task.session_id,
        "user_id": task.user_id,
        "model": resp.model,
        "response": resp.response,
        "usage": {
            "prompt_tokens": int(getattr(resp.usage, "prompt_tokens", 0) or 0) if resp.usage else 0,
            "completion_tokens": int(getattr(resp.usage, "completion_tokens", 0) or 0) if resp.usage else 0,
            "total_tokens": int(getattr(resp.usage, "total_tokens", 0) or 0) if resp.usage else 0,
        },
    }
    await _publish(channel, ROUTING_GENERATED, gen_payload)
    logger.info(f"[worker] chat.generated 事件已发布: session_id={task.session_id}")


async def _publish(channel: aio_pika.Channel, routing_key: str, payload: Dict[str, Any]) -> None:
    """发布消息到指定 routing（用于重试/DLQ 转发）。"""
    exchange = await channel.declare_exchange(EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True)
    msg = aio_pika.Message(
        body=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        content_type="application/json",
        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        type="chat.generate.v1",
    )
    await exchange.publish(msg, routing_key=routing_key)


async def run_consumer() -> None:
    """启动 MQ 消费者（入参：无；出参：无，协程常驻）。"""
    url = (
        f"amqp://{_env('RABBITMQ_USERNAME','guest')}:{_env('RABBITMQ_PASSWORD','guest')}@"
        f"{_env('RABBITMQ_HOST','127.0.0.1')}:{int(_env('RABBITMQ_PORT','5672'))}/{_env('RABBITMQ_VHOST','%2F')}"
    )
    connection: aio_pika.RobustConnection = await aio_pika.connect_robust(url)
    channel: aio_pika.Channel = await connection.channel()
    await channel.set_qos(prefetch_count=4)

    exchange = await channel.declare_exchange(EXCHANGE, aio_pika.ExchangeType.DIRECT, durable=True)
    queue = await channel.declare_queue(ROUTING_GENERATE, durable=True)
    await queue.bind(exchange, routing_key=ROUTING_GENERATE)

    llm = get_llm_service()

    async with queue.iterator() as queue_iter:
        async for msg in queue_iter:
            async with msg.process(ignore_processed=True):
                try:
                    await _handle_message(msg.body, llm, channel)
                except (asyncio.TimeoutError,) as e:
                    # 临时错误：投递到 retry 队列
                    logger.warning(f"temporary error, will retry: {e}")
                    try:
                        payload = json.loads(msg.body.decode("utf-8"))
                        # 维护重试计数
                        headers = dict(msg.headers or {})
                        retry = int(headers.get("x-retry-count", 0)) + 1
                        payload["x-retry-count"] = retry
                        if retry <= 5:
                            await _publish(channel, ROUTING_RETRY, payload)
                        else:
                            await _publish(channel, ROUTING_DLQ, payload)
                    except Exception as ie:
                        logger.error(f"retry/dlq publish failed: {ie}")
                except Exception as e:
                    # 不可恢复错误：DLQ
                    logger.error(f"unrecoverable error, send to DLQ: {e}")
                    try:
                        payload = json.loads(msg.body.decode("utf-8"))
                        await _publish(channel, ROUTING_DLQ, payload)
                    except Exception as ie:
                        logger.error(f"dlq publish failed: {ie}")


def main() -> None:
    try:
        logger.info("[worker] starting chat.generate consumer")
        asyncio.run(run_consumer())
    except KeyboardInterrupt:
        logger.info("[worker] stopped by user")


if __name__ == "__main__":
    main()
