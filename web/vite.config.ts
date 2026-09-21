import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/agents': {
        target: 'http://127.0.0.1:8200',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      '/api': {
        target: 'http://127.0.0.1:8200',
      },
    },
  },
})
