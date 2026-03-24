import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-antd': ['antd', '@ant-design/icons'],
          'vendor-charts': ['echarts', 'echarts-for-react'],
          'vendor-i18n': ['i18next', 'react-i18next', 'i18next-browser-languagedetector'],
        },
      },
    },
    chunkSizeWarningLimit: 600,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('error', (err, _req, res) => {
            console.warn('[Vite proxy] 后端不可达，请确认 backend 已启动 (mvn spring-boot:run):', err.message);
            const resHttp = res as { headersSent?: boolean; writeHead?: (code: number, h: object) => void; end?: (s: string) => void } | null;
            if (resHttp && typeof resHttp.writeHead === 'function' && !resHttp.headersSent) {
              resHttp.writeHead(502, { 'Content-Type': 'application/json' });
              resHttp.end?.(JSON.stringify({ success: false, message: '后端服务未响应，请确认已启动' }));
            }
          });
        },
      },
      '/ws': {
        target: 'ws://localhost:8080',
        changeOrigin: true,
        ws: true,
      }
    }
  }
})
