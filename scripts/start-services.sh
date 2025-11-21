#!/bin/bash
# ================================================================
# Chat Microservices - 启动脚本
# 使用方法: ./scripts/start-services.sh
# ================================================================

set -e

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🚀 启动 Chat Microservices..."
echo "项目根目录: $PROJECT_ROOT"

# 加载环境变量
if [ -f "$PROJECT_ROOT/.env.production" ]; then
    echo "📝 加载环境变量: $PROJECT_ROOT/.env.production"
    source "$PROJECT_ROOT/.env.production"
else
    echo "❌ 错误: 找不到 .env.production 文件"
    exit 1
fi

# 检查必要的端口是否被占用
check_port() {
    local port=$1
    local service=$2
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null; then
        echo "⚠️  警告: 端口 $port ($service) 已被占用"
        return 1
    fi
    return 0
}

echo "🔍 检查端口占用情况..."
check_port $GATEWAY_PORT "Gateway"
check_port $USER_SERVICE_PORT "User Service"
check_port $CHAT_SERVICE_PORT "Chat Service"
check_port $DATA_SERVICE_PORT "Data Service"
check_port $LLM_SERVICE_PORT "LLM Service"

# 切换到项目目录
cd "$PROJECT_ROOT"

# 创建日志目录
mkdir -p logs

echo "🔄 启动 Java 服务..."

# 启动 Gateway Service
echo "▶️  启动 Gateway Service (端口: $GATEWAY_PORT)"
nohup java -jar java-services/gateway-service/target/gateway-service.jar > logs/gateway.log 2>&1 &
echo $! > logs/gateway.pid

# 启动 User Service
echo "▶️  启动 User Service (端口: $USER_SERVICE_PORT)"
nohup java -jar java-services/user-service/target/user-service.jar > logs/user.log 2>&1 &
echo $! > logs/user.pid

# 启动 Data Service
echo "▶️  启动 Data Service (端口: $DATA_SERVICE_PORT)"
nohup java -jar java-services/data-service/target/data-service.jar > logs/data.log 2>&1 &
echo $! > logs/data.pid

# 启动 Chat Service
echo "▶️  启动 Chat Service (端口: $CHAT_SERVICE_PORT)"
nohup java -jar java-services/chat-service/target/chat-service.jar > logs/chat.log 2>&1 &
echo $! > logs/chat.pid

echo "⏳ 等待 Java 服务启动 (10秒)..."
sleep 10

# 启动 Python LLM Service
echo "▶️  启动 LLM Service (端口: $LLM_SERVICE_PORT)"
cd python-services/llm-service

# 检查 Python 虚拟环境
if [ ! -d "venv" ]; then
    echo "📦 创建 Python 虚拟环境..."
    python3 -m venv venv
fi

echo "🔌 激活虚拟环境并安装依赖..."
source venv/bin/activate
pip install -r requirements.txt

# 启动 FastAPI 应用
echo "🐍 启动 FastAPI 应用..."
nohup python -m uvicorn app.main:app --host 0.0.0.0 --port $LLM_SERVICE_PORT > ../../logs/llm.log 2>&1 &
echo $! > ../../logs/llm.pid

# 启动 Worker
echo "🔧 启动 MQ Worker..."
nohup python -m app.worker_mq > ../../logs/worker.log 2>&1 &
echo $! > ../../logs/worker.pid

cd "$PROJECT_ROOT"

echo "✅ 所有服务启动完成!"
echo ""
echo "📊 服务状态:"
echo "  - Gateway:    http://localhost:$GATEWAY_PORT"
echo "  - User:       http://localhost:$USER_SERVICE_PORT"
echo "  - Chat:       http://localhost:$CHAT_SERVICE_PORT"
echo "  - Data:       http://localhost:$DATA_SERVICE_PORT"
echo "  - LLM:        http://localhost:$LLM_SERVICE_PORT"
echo ""
echo "📝 日志文件位置: $PROJECT_ROOT/logs/"
echo "🛑 停止服务: ./scripts/stop-services.sh"