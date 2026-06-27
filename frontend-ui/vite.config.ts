import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { ProxyOptions } from 'vite'

/** 由 ai-gateway（FastAPI）提供；若走 Spring 8082 会得到 404（未注册该路由） */
const AI_GATEWAY_PROXY_PATHS = [
  '/api/ai/prompt-test',
  '/api/ai/prompt-ab-eval',
  '/api/ai/document-process',
  '/api/ai/rag-search',
  '/api/ai/token-probability-tree',
] as const

function makeGatewayProxy(gatewayTarget: string): ProxyOptions {
  return {
    target: gatewayTarget,
    changeOrigin: true,
    ws: false,
    configure(proxy) {
      proxy.on('error', (err, _req, res) => {
        const r = res as ServerResponse | undefined
        if (!r || r.headersSent || typeof r.writeHead !== 'function') return
        r.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
        r.end(
          JSON.stringify({
            code: 'PROXY_UPSTREAM_DOWN',
            message: `无法连接 ai-gateway（${gatewayTarget}）。请先启动：cd ai-gateway && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000。系统错误：${err.message}`,
          }),
        )
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_BACKEND_BASE_URL || 'http://127.0.0.1:8082'
  const gatewayTarget = env.VITE_AI_GATEWAY_BASE_URL || 'http://127.0.0.1:8000'

  /** 显式按路径转发到 8000，避免依赖 http-proxy 的 router（在部分环境下未生效，导致误走 8082 → 404） */
  const gatewayProxies = Object.fromEntries(
    AI_GATEWAY_PROXY_PATHS.map((p) => [p, makeGatewayProxy(gatewayTarget)]),
  ) as Record<string, ProxyOptions>

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      /** 占满 VITE_PORT 时直接失败，避免静默改用 5174 导致与 .env、localStorage 不一致 */
      strictPort: true,
      port: Number(env.VITE_PORT || 5173),
      proxy: {
        ...gatewayProxies,
        /**
         * 其余 /api 走 Spring Boot；网关路径必须在上方单独列出（更具体的路径优先匹配）
         */
        '/api': {
          target: backendTarget,
          changeOrigin: true,
          ws: false,
          configure: (proxy) => {
            proxy.on('error', (err, req, res) => {
              const r = res as ServerResponse | undefined
              if (!r || r.headersSent || typeof r.writeHead !== 'function') return
              r.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
              r.end(
                JSON.stringify({
                  code: 'PROXY_UPSTREAM_DOWN',
                  message: `无法连接 backend-services（${backendTarget}）。请先启动后端（默认 8082）。系统错误：${err.message}`,
                }),
              )
            })
          },
        },
        '/ws': {
          target: env.VITE_BACKEND_BASE_URL || 'http://127.0.0.1:8082',
          ws: true,
          changeOrigin: true,
        },
      },
    },
    build: {
      chunkSizeWarningLimit: 1800,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return undefined
            if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element-plus'
            if (id.includes('echarts') || id.includes('d3') || id.includes('three')) return 'vendor-visualization'
            if (id.includes('mermaid') || id.includes('marked') || id.includes('dompurify')) return 'vendor-markdown'
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vendor-vue'
            return 'vendor'
          },
        },
      },
    },
  }
})
