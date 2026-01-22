import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5273,
    allowedHosts: ['gh-arm', '100.65.126.126', '.local'],
  },
})
