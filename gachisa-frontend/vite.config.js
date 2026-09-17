import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Spring 백엔드(8080)로 /api 요청을 프록시 -> 로컬 개발 시 CORS 이슈 없이 통신
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
      // 상품 이미지 등 백엔드가 /images/** 로 서빙하는 정적 파일
      '/images': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
      // 챗봇은 별도 FastAPI 서버(8000). SSE 스트림을 Spring을 거치지 않고 직접 받는다.
      //
      // 기본값은 로컬 실행용이다. 도커로 띄우면 컨테이너 안의 localhost 가 컨테이너
      // 자신이라 닿지 않으므로, docker-compose 가 서비스 이름(chatbot:8000)을 넣어 준다.
      '/chat': {
        target: process.env.VITE_CHAT_PROXY_TARGET || 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
})
