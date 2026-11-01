import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// 按需注册：项目实际只用到这几个组件，直接引子路径避免整包进入产物
import ElCheckbox from 'element-plus/es/components/checkbox/index'
import ElIcon from 'element-plus/es/components/icon/index'
import ElInput from 'element-plus/es/components/input/index'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/input/style/css'
// ElMessage / ElMessageBox 是函数式调用，仍需全局引入样式
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'

import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ChatLineRound,
  Check,
  Delete,
  Plus,
  Promotion,
  SwitchButton,
  VideoPause,
  VideoPlay
} from '@element-plus/icons-vue'
import './style.css'

const app = createApp(App)
app.use(router)

for (const [name, component] of Object.entries({
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ChatLineRound,
  Check,
  Delete,
  Plus,
  Promotion,
  SwitchButton,
  VideoPause,
  VideoPlay
})) {
  // 与模板中的大写标签名保持一致
  app.component(name, component)
}

app.component('ElCheckbox', ElCheckbox)
app.component('ElIcon', ElIcon)
app.component('ElInput', ElInput)

app.mount('#app')
