import { createApp } from 'vue'
import { ElDrawer, ElInput } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'

createApp(App).use(router).use(ElInput).use(ElDrawer).mount('#app')
