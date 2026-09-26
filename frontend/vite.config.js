import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    },
    historyApiFallback: true
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('vue') || id.includes('vue-router')) return 'vendor-vue'
          if (id.includes('element-plus') || id.includes('@element-plus/icons-vue')) return 'vendor-element'
          if (id.includes('axios')) return 'vendor-axios'
          return 'vendor-others'
        }
      }
    }
  }
})