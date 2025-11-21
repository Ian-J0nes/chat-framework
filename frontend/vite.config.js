import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
// https://vitejs.dev/config/
export default defineConfig({
    plugins: [vue()],
    server: {
        port: 5173,
        proxy: {
            // Dev proxy: prioritize LLM direct → 8080, then fallback other /api → 8081 (Gateway)
            '/api/llm': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            // Other APIs via Gateway 8081
            '/api': {
                target: 'http://localhost:8081',
                changeOrigin: true
            }
        }
    }
});
