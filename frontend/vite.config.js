import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// The app is served from /frontend/ (frontend.war) and talks to the backend at /cube
// (cube.war) on the same WebLogic origin. In development, Vite proxies /cube to a
// locally running backend (default http://127.0.0.1:8080, override with CUBE_BACKEND_URL).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.CUBE_BACKEND_URL || 'http://127.0.0.1:8080'

  return {
    base: '/frontend/',
    plugins: [react()],
    build: { chunkSizeWarningLimit: 1000 },
    server: {
      host: '0.0.0.0',
      proxy: {
        '/cube': { target: backend, changeOrigin: true },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.js'],
      testTimeout: 30000,
    },
  }
})
