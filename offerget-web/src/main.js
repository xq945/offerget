import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import '@fontsource-variable/inter'
import 'element-plus/dist/index.css'
import '@/styles/tailwind.css'
import '@/styles/app.css'

import App from '@/App.vue'
import router from '@/router'
import { setUnauthorizedHandler } from '@/api/request'
import { useUserStore } from '@/stores/user'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 401 统一处理：清除登录态并跳转登录页（由应用层注入，避免 request → router 循环依赖）
setUnauthorizedHandler(() => {
  const userStore = useUserStore()
  userStore.logout()
  const current = router.currentRoute.value
  if (current.meta.public) {
    return
  }
  router.replace({ name: 'login', query: { redirect: current.fullPath } })
})

app.mount('#app')
