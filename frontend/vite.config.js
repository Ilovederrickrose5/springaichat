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
          const inPkg = (name) => id.includes(`node_modules/${name}`)
          // @vueuse 也含 "vue"，必须显式归到 element 侧，否则会与 element-plus 形成循环 chunk
          if (inPkg('element-plus') || inPkg('@element-plus') || inPkg('@vueuse')) return 'vendor-element'
          if (inPkg('vue') || inPkg('@vue/')) return 'vendor-vue'
          if (inPkg('axios')) return 'vendor-axios'
          return 'vendor-others'
        }
      }
    }
  }
})