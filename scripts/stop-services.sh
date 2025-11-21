#!/bin/bash
# ================================================================
# Chat Microservices - 停止服务脚本
# 使用方法: ./scripts/stop-services.sh
# ================================================================

set -e

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🛑 停止 Chat Microservices..."

cd "$PROJECT_ROOT"

# 停止服务函数
stop_service() {
    local service=$1
    local pidfile="logs/${service}.pid"

    if [ -f "$pidfile" ]; then
        local pid=$(cat "$pidfile")
        if kill -0 "$pid" 2>/dev/null; then
            echo "🔻 停止 $service (PID: $pid)"
            kill "$pid"
            # 等待进程结束
            for i in {1..10}; do
                if ! kill -0 "$pid" 2>/dev/null; then
                    break
                fi
                sleep 1
            done
            # 强制结束
            if kill -0 "$pid" 2>/dev/null; then
                echo "⚡ 强制结束 $service"
                kill -9 "$pid" 2>/dev/null || true
            fi
        else
            echo "ℹ️  $service 进程不存在 (PID: $pid)"
        fi
        rm -f "$pidfile"
    else
        echo "ℹ️  $service PID 文件不存在"
    fi
}

# 停止所有服务
stop_service "gateway"
stop_service "user"
stop_service "chat"
stop_service "data"
stop_service "llm"
stop_service "worker"

# 清理可能遗留的进程
echo "🧹 清理遗留进程..."
pkill -f "gateway-service.jar" 2>/dev/null || true
pkill -f "user-service.jar" 2>/dev/null || true
pkill -f "chat-service.jar" 2>/dev/null || true
pkill -f "data-service.jar" 2>/dev/null || true
pkill -f "uvicorn app.main:app" 2>/dev/null || true
pkill -f "app.worker_mq" 2>/dev/null || true

echo "✅ 所有服务已停止"