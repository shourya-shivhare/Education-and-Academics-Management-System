import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api → Spring Boot at localhost:8080 during development.
// In production, configure nginx/Apache to proxy accordingly.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
