import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Spring 백엔드(8080)로 /api 요청을 프록시 -> 로컬 개발 시 CORS 이슈 없이 통신
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 상품 이미지 등 백엔드가 /images/** 로 서빙하는 정적 파일
      '/images': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
