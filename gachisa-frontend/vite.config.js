import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Spring 백엔드(8080)로 /api 요청을 프록시 -> 로컬 개발 시 CORS 이슈 없이 통신
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // 대기열은 별도 Kotlin 서비스(8081). 아래 '/api' 규칙보다 먼저 매칭돼야 하므로
      // 더 구체적인 이 항목을 위에 둔다.
      '^/api/group-buys/[0-9]+/queue-token': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 상품 이미지 등 백엔드가 /images/** 로 서빙하는 정적 파일
      '/images': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 챗봇은 별도 FastAPI 서버(8000). SSE 스트림을 Spring을 거치지 않고 직접 받는다.
      '/chat': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
})
