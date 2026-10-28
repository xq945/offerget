<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  FileText,
  Lock,
  ShieldCheck,
  Sparkles,
  Target,
  User,
} from 'lucide-vue-next'

import BrandLogo from '@/components/BrandLogo.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref('login')
const showPassword = ref(false)
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  graduationYear: 2027,
})

const isLogin = computed(() => mode.value === 'login')
const title = computed(() => (isLogin.value ? '欢迎回来' : '创建你的 OfferGet 账号'))
const subtitle = computed(() =>
  isLogin.value
    ? '登录后继续推进今天的投递节奏'
    : '注册后即可管理岗位、简历版本与投递进度',
)

const yearOptions = Array.from({ length: 8 }, (_, index) => 2025 + index)

function switchMode(next) {
  mode.value = next
  form.password = ''
}

function fillDemo() {
  form.username = 'demo'
  form.password = 'demo123'
}

function validate() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return false
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return false
  }
  if (!isLogin.value) {
    if (!/^[a-zA-Z0-9_]{3,20}$/.test(form.username.trim())) {
      ElMessage.warning('用户名需为 3-20 位字母、数字或下划线')
      return false
    }
    if (!form.nickname.trim()) {
      ElMessage.warning('请输入昵称')
      return false
    }
    if (form.password.length < 6) {
      ElMessage.warning('密码至少 6 位')
      return false
    }
  }
  return true
}

async function submit() {
  if (!validate()) {
    return
  }
  submitting.value = true
  try {
    if (isLogin.value) {
      await userStore.login({
        username: form.username.trim(),
        password: form.password,
      })
      ElMessage.success(`欢迎回来，${userStore.displayName}`)
    }
    else {
      await userStore.register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim(),
        graduationYear: form.graduationYear,
      })
      ElMessage.success('注册成功，已为你创建专属工作台')
    }
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/app/dashboard'
    router.replace(redirect)
  }
  catch (error) {
    ElMessage.error(error.message || '操作失败，请稍后重试')
  }
  finally {
    submitting.value = false
  }
}

</script>

<template>
  <div class="relative min-h-screen overflow-hidden page-bg">
    <div class="pointer-events-none absolute -left-32 top-[-10rem] h-[26rem] w-[26rem] rounded-full bg-blue-200/40 blur-3xl" />
    <div class="pointer-events-none absolute -right-24 bottom-[-12rem] h-[30rem] w-[30rem] rounded-full bg-sky-200/40 blur-3xl" />

    <div class="relative mx-auto flex min-h-screen w-full max-w-6xl flex-col px-5 py-6 sm:px-8">
      <header class="flex items-center justify-between">
        <BrandLogo />
        <router-link
          to="/"
          class="inline-flex items-center gap-2 rounded-full border border-blue-100 bg-white/80 px-4 py-2 text-sm font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700"
        >
          <ArrowLeft class="h-4 w-4" />
          返回主页
        </router-link>
      </header>

      <main class="flex flex-1 items-center justify-center py-10">
        <div class="grid w-full max-w-5xl overflow-hidden rounded-[28px] border border-blue-100 bg-white/80 shadow-[0_32px_80px_rgba(37,99,235,0.16)] backdrop-blur lg:grid-cols-[1.02fr_1fr]">
          <!-- 品牌面板 -->
          <section class="relative hidden flex-col justify-between overflow-hidden bg-gradient-to-br from-blue-600 via-blue-500 to-sky-400 p-10 text-white lg:flex">
            <div class="pointer-events-none absolute -right-16 top-10 h-56 w-56 rounded-full border border-white/20" />
            <div class="pointer-events-none absolute -bottom-24 -left-10 h-64 w-64 rounded-full bg-white/10 blur-2xl" />

            <div class="relative">
              <span class="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/10 px-4 py-1.5 text-xs font-semibold backdrop-blur">
                <Sparkles class="h-3.5 w-3.5" />
                2027 届校招工作台
              </span>
              <h2 class="mt-6 text-3xl font-black leading-tight">
                把整个校招<br />收进一个工作台
              </h2>
              <p class="mt-4 max-w-sm text-sm leading-6 text-blue-50/90">
                岗位、简历版本、AI 匹配与面试跟进统一管理，不再散落在 Excel、备忘录和聊天记录里。
              </p>
            </div>

            <div class="relative mt-10 space-y-4">
              <div class="flex items-start gap-3">
                <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white/15 backdrop-blur">
                  <Target class="h-4.5 w-4.5" />
                </span>
                <div>
                  <p class="text-sm font-bold">投递进度一目了然</p>
                  <p class="mt-0.5 text-xs text-blue-50/80">截止、笔试、面试节点自动汇总，不再错过关键日期。</p>
                </div>
              </div>
              <div class="flex items-start gap-3">
                <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white/15 backdrop-blur">
                  <Sparkles class="h-4.5 w-4.5" />
                </span>
                <div>
                  <p class="text-sm font-bold">AI 匹配定位差距</p>
                  <p class="mt-0.5 text-xs text-blue-50/80">对照 JD 找出命中与缺失关键词，明确简历修改方向。</p>
                </div>
              </div>
              <div class="flex items-start gap-3">
                <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white/15 backdrop-blur">
                  <FileText class="h-4.5 w-4.5" />
                </span>
                <div>
                  <p class="text-sm font-bold">多版本简历管理</p>
                  <p class="mt-0.5 text-xs text-blue-50/80">按岗位方向维护不同版本，投递时精准绑定。</p>
                </div>
              </div>
            </div>

            <p class="relative mt-10 flex items-center gap-2 text-xs text-blue-50/70">
              <ShieldCheck class="h-4 w-4" />
              账号数据仅存储在你自己的部署环境中
            </p>
          </section>

          <!-- 表单面板 -->
          <section class="flex flex-col justify-center p-7 sm:p-10">
            <div>
              <h1 class="text-2xl font-black text-slate-950">{{ title }}</h1>
              <p class="mt-2 text-sm text-slate-500">{{ subtitle }}</p>
            </div>

            <!-- 账号密码 / 注册 -->
            <form class="mt-6 space-y-4" @submit.prevent="submit">
              <div>
                <label class="mb-1.5 block text-xs font-bold text-slate-600">用户名</label>
                <div class="flex items-center gap-2.5 rounded-xl border border-blue-100 bg-white px-3.5 py-2.5 transition focus-within:border-blue-400 focus-within:ring-4 focus-within:ring-blue-500/10">
                  <User class="h-4 w-4 shrink-0 text-slate-400" />
                  <input
                    v-model="form.username"
                    type="text"
                    autocomplete="username"
                    placeholder="请输入用户名"
                    class="w-full bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
                  />
                </div>
              </div>

              <div v-if="!isLogin" class="grid gap-4 sm:grid-cols-2">
                <div>
                  <label class="mb-1.5 block text-xs font-bold text-slate-600">昵称</label>
                  <div class="flex items-center gap-2.5 rounded-xl border border-blue-100 bg-white px-3.5 py-2.5 transition focus-within:border-blue-400 focus-within:ring-4 focus-within:ring-blue-500/10">
                    <User class="h-4 w-4 shrink-0 text-slate-400" />
                    <input
                      v-model="form.nickname"
                      type="text"
                      placeholder="怎么称呼你"
                      class="w-full bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
                    />
                  </div>
                </div>
                <div>
                  <label class="mb-1.5 block text-xs font-bold text-slate-600">毕业年份</label>
                  <select
                    v-model="form.graduationYear"
                    class="w-full rounded-xl border border-blue-100 bg-white px-3.5 py-2.5 text-sm text-slate-800 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-500/10"
                  >
                    <option v-for="year in yearOptions" :key="year" :value="year">{{ year }} 届</option>
                  </select>
                </div>
              </div>

              <div>
                <label class="mb-1.5 block text-xs font-bold text-slate-600">密码</label>
                <div class="flex items-center gap-2.5 rounded-xl border border-blue-100 bg-white px-3.5 py-2.5 transition focus-within:border-blue-400 focus-within:ring-4 focus-within:ring-blue-500/10">
                  <Lock class="h-4 w-4 shrink-0 text-slate-400" />
                  <input
                    v-model="form.password"
                    :type="showPassword ? 'text' : 'password'"
                    :autocomplete="isLogin ? 'current-password' : 'new-password'"
                    :placeholder="isLogin ? '请输入密码' : '至少 6 位'"
                    class="w-full bg-transparent text-sm text-slate-800 outline-none placeholder:text-slate-400"
                  />
                  <button type="button" class="text-slate-400 transition hover:text-slate-600" @click="showPassword = !showPassword">
                    <EyeOff v-if="showPassword" class="h-4 w-4" />
                    <Eye v-else class="h-4 w-4" />
                  </button>
                </div>
              </div>

              <button
                type="submit"
                :disabled="submitting"
                class="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-sky-500 px-5 py-3 text-sm font-bold text-white shadow-[0_14px_28px_rgba(37,99,235,0.24)] transition hover:brightness-105 disabled:cursor-not-allowed disabled:opacity-70"
              >
                <CheckCircle2 v-if="!submitting" class="h-4 w-4" />
                <span v-if="submitting" class="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
                {{ submitting ? '处理中…' : isLogin ? '登录' : '注册并进入工作台' }}
              </button>

              <div class="flex items-center justify-between text-xs">
                <button type="button" class="font-semibold text-slate-500 transition hover:text-blue-700" @click="switchMode(isLogin ? 'register' : 'login')">
                  {{ isLogin ? '还没有账号？立即注册' : '已有账号？直接登录' }}
                </button>
                <button
                  v-if="isLogin"
                  type="button"
                  class="rounded-full bg-blue-50 px-3 py-1 font-semibold text-blue-600 transition hover:bg-blue-100"
                  @click="fillDemo"
                >
                  填入演示账号
                </button>
              </div>
            </form>

            <div class="mt-6 rounded-xl border border-blue-100 bg-blue-50/50 px-4 py-3 text-xs text-slate-500">
              <p class="font-semibold text-slate-600">演示账号</p>
              <p class="mt-1">用户名 <span class="font-mono text-blue-700">demo</span> · 密码 <span class="font-mono text-blue-700">demo123</span></p>
            </div>
          </section>
        </div>
      </main>

      <footer class="pb-2 text-center text-xs text-slate-400">
        OfferGet · 复刻自 offerbiu 交互与信息架构的 Vue + Java 实现
      </footer>
    </div>
  </div>
</template>
