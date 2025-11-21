# Chat Microservices

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Python](https://img.shields.io/badge/Python-3.10+-blue.svg)](https://www.python.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.x-4FC08D.svg)](https://vuejs.org/)

基于微服务架构的智能聊天系统，支持多模型 LLM 对话、RAG 检索增强生成、Function Calling，采用异步消息队列解耦，提供完整的用户认证、会话管理、限流防护等企业级特性。

---

## 📋 目录

- [核心特性](#核心特性)
- [架构设计](#架构设计)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [功能详解](#功能详解)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [开发指南](#开发指南)
- [部署建议](#部署建议)
- [常见问题](#常见问题)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

---

## 🎯 核心特性

### 业务功能

- **多轮对话**：支持上下文记忆（可配置窗口大小），智能续写历史对话
- **多模型支持**：动态获取可用模型列表，支持 OpenAI 及兼容 API
- **Function Calling**：内置工具函数（时间查询、数学计算、网站检测、网络搜索等）
- **RAG 检索增强**：集成 Chroma 向量库，支持文本入库与相似度查询
- **用户认证**：JWT + Redis 会话缓存 + 黑名单机制
- **会话管理**：多会话切换、历史记录持久化

### 技术特性

- **异步解耦**：RabbitMQ 消息队列，聊天请求立即返回 202，后台异步生成
- **限流防刷**：Redis 令牌桶算法，按用户/IP 限制请求频率
- **幂等保证**：`request_id` 防止重复提交，消息去重
- **全局异常处理**：统一 `@ControllerAdvice` 拦截，标准化错误响应
- **构造器注入**：遵循 Spring 最佳实践，便于单元测试
- **模块化设计**：前后端分离，服务职责清晰

---

## 🏗️ 架构设计

### 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户访问层                               │
│                    http://localhost:5173                        │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    前端 (Vue 3 + Vite)                          │
│  - 登录/注册 → JWT Token 管理                                    │
│  - 聊天界面 → WebSocket/HTTP 轮询                                │
│  - 会话列表 → 多会话切换                                          │
│  - 模型选择 → 动态获取可用模型                                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              API 网关 (Spring Cloud Gateway:8081)               │
│  - 路由转发                                                      │
│  - 限流防刷 (Redis 令牌桶)                                        │
│  - CORS 跨域处理                                                 │
└───┬────────────┬─────────────┬─────────────┬────────────────────┘
    │            │             │             │
    ▼            ▼             ▼             ▼
┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
│  User   │ │   Chat   │ │   Data   │ │     LLM      │
│ Service │ │ Service  │ │ Service  │ │   Service    │
│  :8082  │ │  :8084   │ │  :8083   │ │    :8080     │
└────┬────┘ └─────┬────┘ └─────┬────┘ └──────┬───────┘
     │            │             │             │
     │            └─────────────┴─────────────┘
     │                      │
     ▼                      ▼
┌─────────┐        ┌──────────────┐
│  MySQL  │        │   RabbitMQ   │
│ (Users) │        │ (异步任务队列)│
└─────────┘        └──────┬───────┘
                          │
                          ▼
                  ┌──────────────────┐
                  │  LLM Worker (MQ) │
                  │  - 消费生成任务   │
                  │  - 调用 OpenAI   │
                  │  - 发布结果事件   │
                  └──────────────────┘
                          │
                          ▼
                  ┌──────────────────┐
                  │   Chroma (RAG)   │
                  │   向量数据库      │
                  └──────────────────┘
```

### 消息流转

```
用户发送消息
    ↓
Gateway (限流校验)
    ↓
Chat Service
    ├─ 保存用户消息 (Data Service)
    ├─ 拉取会话历史 (Data Service)
    └─ 发布 MQ 任务 → RabbitMQ (chat.generate)
         ↓ (立即返回 202)
    ┌────┴────────────────────────────┐
    │    LLM Worker (Python)          │
    │  1. 消费 chat.generate          │
    │  2. 可选 RAG 注入上下文          │
    │  3. 调用 OpenAI API             │
    │  4. 发布 chat.generated 事件     │
    └────┬────────────────────────────┘
         ↓
    Data Service (消费 chat.generated)
    ├─ 保存助手消息 (幂等)
    └─ 更新 token 用量统计
         ↓
    前端轮询拉取新消息
```

---

## 🛠️ 技术栈

### 后端服务

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **API 网关** | Spring Cloud Gateway | 2023.x | 动态路由、限流、CORS |
| **微服务框架** | Spring Boot | 3.2+ | 核心业务服务 |
| **服务注册** | Nacos | 2.x | 服务发现与配置中心 |
| **服务调用** | OpenFeign | 4.x | 声明式 HTTP 客户端 |
| **ORM** | MyBatis-Plus | 3.5+ | 增强 MyBatis |
| **认证** | JWT (java-jwt) | 4.x | 无状态令牌 |
| **消息队列** | RabbitMQ | 3.x | 异步任务解耦 |
| **缓存** | Redis | 6+ | 会话缓存、限流、黑名单 |
| **AI 服务** | FastAPI | 0.110+ | Python 微服务 |
| **LLM SDK** | OpenAI Python | 1.x | 调用 OpenAI API |
| **向量库** | Chroma | 0.4+ | RAG 检索增强 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue.js | 3.x | 渐进式框架 |
| TypeScript | 5.x | 类型安全 |
| Vite | 5.x | 快速构建 |
| Vue Router | 4.x | 路由管理 |

### 数据库

- **MySQL 8.0+**：用户数据、会话记录、消息历史、统计数据

---

## 📁 项目结构

```
chat-microservices/
├── frontend/                          # Vue 3 前端
│   ├── src/
│   │   ├── api/client.ts             # API 客户端封装
│   │   ├── router/index.ts           # 路由配置（鉴权守卫）
│   │   ├── views/
│   │   │   ├── AuthView.vue          # 登录/注册页
│   │   │   └── ChatView.vue          # 聊天主页
│   │   └── App.vue
│   ├── .env.example                  # 环境变量示例
│   └── package.json
│
├── java-services/                     # Java 微服务
│   ├── common-api/                    # 公共模块
│   │   ├── entity/                    # 共享实体 (User, ChatMessage 等)
│   │   ├── result/Result.java         # 统一响应封装
│   │   └── exception/
│   │       ├── BusinessException.java         # 业务异常
│   │       └── GlobalExceptionHandler.java    # 全局异常处理
│   │
│   ├── gateway-service/               # API 网关 (8081)
│   │   ├── config/RateLimitConfig.java       # 限流配置
│   │   └── application.yml
│   │
│   ├── user-service/                  # 用户认证服务 (8082)
│   │   ├── controller/
│   │   │   ├── AuthController.java           # 注册/登录/登出
│   │   │   └── UserController.java           # 用户 CRUD
│   │   ├── service/
│   │   │   ├── AuthService.java              # 认证业务
│   │   │   ├── JwtService.java               # JWT 签发/校验
│   │   │   ├── JwtBlacklistService.java      # 黑名单管理
│   │   │   └── UserSessionCacheService.java  # 会话缓存
│   │   └── application.yml
│   │
│   ├── data-service/                  # 数据持久化服务 (8083)
│   │   ├── controller/DataController.java    # 会话/消息/统计 CRUD
│   │   ├── service/                          # 业务逻辑层
│   │   ├── mapper/                           # MyBatis Mapper
│   │   └── mq/
│   │       └── ChatGeneratedListener.java    # 消费 chat.generated 事件
│   │
│   └── chat-service/                  # 聊天业务服务 (8084)
│       ├── controller/ChatController.java    # 聊天接口
│       ├── client/
│       │   ├── DataServiceClient.java        # Feign 客户端
│       │   └── LLMServiceClient.java
│       └── mq/
│           ├── RabbitConfig.java             # MQ 拓扑声明
│           └── ChatTaskPublisher.java        # 发布 chat.generate 任务
│
├── python-services/                   # Python 微服务
│   └── llm-service/                   # LLM & RAG 服务 (8080)
│       ├── app/
│       │   ├── main.py                       # FastAPI 入口
│       │   ├── models.py                     # Pydantic 模型
│       │   ├── llm_service.py                # LLM 服务
│       │   ├── rag_service.py                # RAG 服务
│       │   ├── functions.py                  # Function Calling
│       │   ├── worker_mq.py                  # MQ 消费者 (aio-pika)
│       │   └── services.py                   # 兼容层
│       ├── requirements.txt
│       └── .env.example
│
├── shared-schemas/                    # 跨服务契约
│   └── openapi/
│       ├── common-types.yaml
│       └── llm-api.yaml
│
├── db.md                              # 数据库 DDL 脚本
├── .env.production                    # 生产环境配置示例
└── README.md
```

---

## 🚀 快速开始

### 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐使用 OpenJDK |
| Python | 3.10+ | 需要 pip |
| Node.js | 18+ | 需要 npm |
| MySQL | 8.0+ | 数据库 |
| RabbitMQ | 3.12+ | 消息队列 |
| Redis | 6.2+ | 缓存 |
| Nacos | 2.3+ | 服务注册（可选） |

### 1. 数据库初始化

```bash
# 连接到 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE chat_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE chat_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 执行 db.md 中的 DDL 脚本
```

### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.production .env

# 编辑配置（修改数据库、Redis、RabbitMQ 等连接信息）
vim .env
```

关键配置项：

```bash
# 数据库
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_USERNAME=root
MYSQL_PASSWORD=your-password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# OpenAI
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com/v1

# JWT
JWT_SECRET=your-secret-key-at-least-32-chars
JWT_EXPIRES_IN=24h
```

### 3. 启动 Java 服务

```bash
cd java-services

# 构建所有模块
mvn clean install -DskipTests

# 按顺序启动服务（或使用 IDE）
# 1. Gateway Service (8081)
cd gateway-service && mvn spring-boot:run

# 2. User Service (8082)
cd ../user-service && mvn spring-boot:run

# 3. Data Service (8083)
cd ../data-service && mvn spring-boot:run

# 4. Chat Service (8084)
cd ../chat-service && mvn spring-boot:run
```

### 4. 启动 LLM 服务

#### 方式 1：FastAPI Web 服务

```bash
cd python-services/llm-service

# 创建虚拟环境
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
vim .env  # 填入 OPENAI_API_KEY 等

# 启动 API 服务
uvicorn app.main:app --reload --port 8080
```

#### 方式 2：MQ Worker（推荐与 API 同时启动）

```bash
# 另开终端
cd python-services/llm-service
source venv/bin/activate

# 启动 Worker
python -m app.worker_mq
```

### 5. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev
```

访问 http://localhost:5173

### 6. 验证服务

| 服务 | 地址 | 健康检查 |
|------|------|----------|
| Gateway | http://localhost:8081 | `GET /actuator/health` |
| User Service | http://localhost:8082 | `GET /actuator/health` |
| Data Service | http://localhost:8083 | `GET /actuator/health` |
| Chat Service | http://localhost:8084 | `GET /actuator/health` |
| LLM Service | http://localhost:8080 | `GET /health` |
| RabbitMQ 管理 | http://localhost:15672 | 默认 guest/guest |

---

## 📚 功能详解

### 用户认证流程

```
注册
  ↓
POST /api/user/register
  ├─ 校验用户名/邮箱唯一性
  ├─ BCrypt 加密密码
  ├─ 签发 JWT Token
  └─ 缓存会话信息 (Redis)

登录
  ↓
POST /api/user/login
  ├─ 验证用户名/邮箱 + 密码
  ├─ 签发 JWT Token
  └─ 缓存会话信息 (Redis)

获取当前用户
  ↓
GET /api/user/me (Authorization: Bearer <token>)
  ├─ 优先查 Redis 缓存
  ├─ 缓存未命中 → 验证 JWT
  ├─ 检查黑名单
  └─ 返回用户信息

登出
  ↓
POST /api/user/logout
  ├─ 将 JWT 加入黑名单 (Redis)
  └─ 清除会话缓存
```

### 聊天流程（异步模式）

```
1. 用户发送消息
   POST /api/chat/send
   {
     "sessionId": "uuid",
     "user_id": 1,
     "model": "gpt-4o-mini",
     "messages": [{"role": "user", "content": "你好"}],
     "request_id": "uuid"  // 幂等 ID
   }

2. Chat Service 处理
   ├─ 参数校验
   ├─ 确保会话存在
   ├─ 保存用户消息 (Data Service)
   ├─ 拉取最近 N 条历史消息
   ├─ 构建上下文负载
   └─ 发布 MQ 任务 (RabbitMQ: chat.generate)

3. 立即返回 202 Accepted
   {
     "code": 202,
     "message": "accepted",
     "data": {
       "request_id": "uuid",
       "sessionId": "uuid"
     }
   }

4. LLM Worker 异步处理
   ├─ 消费 chat.generate 任务
   ├─ 可选：RAG 注入上下文
   ├─ 调用 OpenAI API
   ├─ 生成回复 + 用量统计
   └─ 发布 chat.generated 事件

5. Data Service 消费结果
   ├─ 保存助手消息 (幂等，避免重复)
   └─ 更新 token 用量统计

6. 前端轮询拉取
   GET /api/data/sessions/{sessionId}/messages
   └─ 展示最新消息
```

### RAG 检索增强

```
入库流程
  ↓
POST /api/rag/ingest
{
  "text": "长文本内容",
  "namespace": "demo",
  "user_id": 1,
  "tags": ["文档", "知识库"]
}
  ├─ 文本切分 (chunk_size=1000, overlap=200)
  ├─ 调用 Embeddings API 生成向量
  └─ 存入 Chroma 向量库

查询流程
  ↓
POST /api/rag/query
{
  "query": "如何使用 RAG？",
  "top_k": 5,
  "namespace": "demo",
  "user_id": 1
}
  ├─ 生成查询向量
  ├─ Chroma 相似度搜索
  └─ 返回最相关的 top_k 个文本块

聊天时自动注入
  ├─ 设置 use_rag=true 或 RAG_DEFAULT_ON=true
  ├─ 检索相关内容
  └─ 在 messages 前插入 system 提示
```

### Function Calling

内置工具函数：

| 函数名 | 说明 | 参数 |
|--------|------|------|
| `get_current_time` | 获取当前时间 | timezone, format |
| `calculate` | 数学计算 | expression, precision |
| `generate_random_password` | 生成随机密码 | length, include_symbols, include_numbers |
| `check_website_status` | 检查网站状态 | url, timeout |
| `web_search` | 网络搜索 (MCP) | query, limit, engines |

使用示例：

```
用户: "现在北京时间是几点？"
  ↓
LLM 判断需要调用 get_current_time
  ↓
执行函数 → {"current_time": "2025-01-15 14:30:00", "timezone": "Asia/Shanghai"}
  ↓
LLM 基于结果生成回复: "现在北京时间是 2025年1月15日 14:30。"
```

---

## ⚙️ 配置说明

### Java 服务配置

#### application.yml 通用结构

```yaml
server:
  port: ${SERVICE_PORT:8082}

spring:
  application:
    name: service-name
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/chat_user
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
```

#### Gateway 限流配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: chat-service
          uri: lb://chat-service
          predicates:
            - Path=/api/chat/**
          filters:
            - name: RequestRateLimiter
              args:
                # 每秒补充 3 个令牌，桶容量 3
                redis-rate-limiter.replenishRate: 3
                redis-rate-limiter.burstCapacity: 3
                # 每次请求消耗 1 个令牌
                redis-rate-limiter.requestedTokens: 1
```

### Python 服务配置

#### .env 示例

```bash
# OpenAI
OPENAI_API_KEY=sk-xxx
OPENAI_BASE_URL=https://api.openai.com/v1

# 模型过滤（正则表达式，不区分大小写）
LLM_ALLOWED_MODEL_REGEX=gpt|o3

# Embeddings（用于 RAG）
EMBEDDINGS_BASE_URL=https://api.openai.com/v1
EMBEDDINGS_API_KEY=sk-xxx
EMBEDDINGS_MODEL=text-embedding-3-small

# Chroma 向量库
CHROMA_BASE_URL=http://localhost:8808/api/v1
CHROMA_COLLECTION=kb_default

# RAG 配置
RAG_CHUNK_SIZE=1000
RAG_CHUNK_OVERLAP=200
RAG_DEFAULT_ON=false  # 是否默认启用 RAG

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/

# Data Service（用于 Worker 回写）
DATA_SERVICE_BASE_URL=http://localhost:8083

# 网络搜索（可选）
WEB_SEARCH_ENABLED=true
```

---

## 📖 API 文档

### 用户认证

#### 注册

```http
POST /api/user/register
Content-Type: application/json

{
  "username": "user001",
  "email": "user@example.com",
  "password": "Password123",
  "nickname": "昵称"
}
```

响应：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "username": "user001",
      "email": "user@example.com",
      "nickname": "昵称"
    }
  }
}
```

#### 登录

```http
POST /api/user/login
Content-Type: application/json

{
  "username": "user001",  // 或使用 email
  "password": "Password123"
}
```

#### 获取当前用户

```http
GET /api/user/me
Authorization: Bearer <token>
```

### 聊天

#### 发送消息

```http
POST /api/chat/send
Authorization: Bearer <token>
Content-Type: application/json

{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": 1,
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "request_id": "req-uuid-001"
}
```

响应：

```json
{
  "code": 202,
  "message": "accepted",
  "data": {
    "request_id": "req-uuid-001",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

#### 获取会话消息

```http
GET /api/data/sessions/{sessionId}/messages
Authorization: Bearer <token>
```

### RAG

#### 文本入库

```http
POST /api/rag/ingest
Content-Type: application/json

{
  "text": "这是要入库的长文本内容...",
  "namespace": "demo",
  "user_id": 1,
  "tags": ["文档"]
}
```

#### 相似度查询

```http
POST /api/rag/query
Content-Type: application/json

{
  "query": "如何使用 RAG？",
  "top_k": 5,
  "namespace": "demo",
  "user_id": 1
}
```
---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/AmazingFeature`
3. 提交改动：`git commit -m 'feat: Add some AmazingFeature'`
4. 推送分支：`git push origin feature/AmazingFeature`
5. 提交 Pull Request

### Commit 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

- `feat`: 新功能
- `fix`: 修复 Bug
- `docs`: 文档更新
- `style`: 代码格式
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具链

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [OpenAI](https://openai.com/)
- [FastAPI](https://fastapi.tiangolo.com/)
- [Chroma](https://www.trychroma.com/)

---

**如有问题或建议，欢迎提交 Issue 或联系维护者！**
