<script setup lang="ts">
// 根组件仅承载路由出口；鉴权由路由守卫处理
</script>

<template>
  <router-view />
</template>

<style>
/* 全局基础样式与主题变量（Anthropic 暖色调风格） */
:root {
  --bg: #fef9f2;
  --bg-gradient: radial-gradient(1200px 600px at 20% -10%, #fff7ed 0%, rgba(255,247,237,0) 50%),
                  radial-gradient(1000px 500px at 120% 10%, #fef3e2 0%, rgba(254,243,226,0) 50%),
                  linear-gradient(180deg, #fefcf8 0%, #fef9f2 100%);
  --card: #ffffff;
  --surface: #fffef9;
  --text: #1c1917;
  --muted: #a8a29e;
  --border: #e7e5e4;
  --primary: #f97316;
  --primary-600: #ea580c;
  --primary-700: #c2410c;
  --accent: #fbbf24;
  --accent-light: #fef3c7;
  --ring: 0 0 0 4px rgba(249,115,22,0.15);
  --shadow: 0 8px 24px rgba(194, 65, 12, 0.08);
  --warm-gradient: linear-gradient(135deg, #fbbf24 0%, #f97316 100%);
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #1c1917;
    --bg-gradient: radial-gradient(1000px 500px at 20% -10%, rgba(249,115,22,0.15) 0%, rgba(249,115,22,0) 50%),
                    radial-gradient(900px 450px at 120% 10%, rgba(251,191,36,0.12) 0%, rgba(251,191,36,0) 50%),
                    linear-gradient(180deg, #1c1917 0%, #292524 100%);
    --card: #292524;
    --surface: #1c1917;
    --text: #fafaf9;
    --muted: #a8a29e;
    --border: #44403c;
    --primary: #fb923c;
    --primary-600: #f97316;
    --primary-700: #ea580c;
    --accent: #fbbf24;
    --accent-light: rgba(251,191,36,0.1);
    --ring: 0 0 0 4px rgba(251,146,60,0.15);
    --shadow: 0 10px 30px rgba(0,0,0,0.4);
    --warm-gradient: linear-gradient(135deg, #fbbf24 0%, #fb923c 100%);
  }
}

html, body {
  height: 100%;
  overflow: hidden; /* 禁止页面级滚动，由内部容器负责 */
}

#app { height: 100dvh; }

body {
  margin: 0;
  color: var(--text);
  background: var(--bg);
  background-image: var(--bg-gradient);
  font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "Apple Color Emoji", "Segoe UI Emoji";
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

button, input, select, textarea { font: inherit; color: inherit; }
button { cursor: pointer; }

/* 自定义滚动条（WebKit 支持，Anthropic 暖色调） */
*::-webkit-scrollbar { width: 10px; height: 10px; }
*::-webkit-scrollbar-thumb {
  background: linear-gradient(135deg, rgba(249,115,22,0.3), rgba(251,191,36,0.3));
  border-radius: 8px;
  border: 2px solid transparent;
  background-clip: padding-box;
}
*::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(135deg, rgba(249,115,22,0.5), rgba(251,191,36,0.5));
}
*::-webkit-scrollbar-track { background: transparent; }
</style>
