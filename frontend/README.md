# Frontend (Vue 3 + Vite + TypeScript)

最小化前端骨架，支持本地开发代理 `/api` → `http://localhost:8081`，并可在 Vercel 部署。

## 本地开发

- 先确保后端网关（Gateway 8081）、chat-service（8083）、data-service（8084）与 llm-service（8080）可用
- 启动前端：

```
cd frontend
npm install
npm run dev
# 打开 http://localhost:5173
```

## 目录结构

- `src/api/client.ts`：封装 `POST /api/chat/send`，严格类型，含入参/出参注释
- `src/App.vue`：最小演示页面（输入文本并调用后端）
- `vite.config.ts`：开发代理（将 `/api` 转发至 Gateway 8081）
- `vercel.json`：单页应用路由回退配置

## 生产部署（Vercel）

- Project Settings：
  - Root Directory: `frontend`
  - Build Command: `npm run build`
  - Output Directory: `dist`
- 环境变量（Production/Preview）：
  - `VITE_API_BASE = https://<你的网关域名或反代>/api`
- CORS：请在网关允许来自 `*.vercel.app` 或你的自定义域的 Origin

## 可配置项

- `VITE_API_BASE`：优先使用该地址作为后端基址；未配置时，默认使用 `/api` 并通过开发代理访问本地 Gateway

```
# .env.example（自行复制为 .env 或在 Vercel 中配置）
VITE_API_BASE=https://your-domain/api
```

## 类型与约定

- 使用 TypeScript；所有导出函数均附入参/出参注释
- 请求体示例（`POST /api/chat/send`）：

```
{
  "sessionId": "demo-session-001",
  "user_id": 1,
  "model": "gpt-4o-mini",
  "messages": [ { "role": "user", "content": "你好" } ]
}
```

- 返回体包裹在后端统一 `Result<T>`：

```
{
  "code": 200,
  "message": "成功",
  "data": { "response": "…", "model": "…", "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0} }
}
```

