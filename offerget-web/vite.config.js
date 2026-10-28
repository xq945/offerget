import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5175,
    proxy: {
      // 开发环境走代理，避免跨域；生产同源部署时无需改动
      '/api': { target: 'http://localhost:8082', changeOrigin: true },
      '/uploads': { target: 'http://localhost:8082', changeOrigin: true },
    },
  },
})
