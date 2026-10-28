import { createRouter, createWebHistory } from 'vue-router'

import MarketingLayout from '@/layouts/MarketingLayout.vue'
import WorkspaceLayout from '@/layouts/WorkspaceLayout.vue'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/',
    component: MarketingLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { public: true, title: '大学生校招投递管理' },
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/app',
    component: WorkspaceLayout,
    redirect: '/app/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '校招总览' },
      },
      {
        path: 'jobs',
        name: 'jobs',
        component: () => import('@/views/JobsView.vue'),
        meta: { title: '我的投递' },
      },
      {
        path: 'companies',
        name: 'companies',
        component: () => import('@/views/CompaniesView.vue'),
        meta: { title: '校招信息库' },
      },
      {
        path: 'resumes',
        name: 'resumes',
        component: () => import('@/views/ResumesView.vue'),
        meta: { title: '简历管理' },
      },
      {
        path: 'resumes/builder',
        name: 'resume-builder',
        component: () => import('@/views/ResumeBuilderView.vue'),
        meta: { title: '简历制作' },
      },
      {
        path: 'resume-autofill',
        name: 'resume-autofill',
        component: () => import('@/views/AutofillView.vue'),
        meta: { title: '自动填充' },
      },
      {
        path: 'ai-match',
        name: 'ai-match',
        component: () => import('@/views/AiMatchView.vue'),
        meta: { title: 'AI 匹配' },
      },
      {
        path: 'optimize',
        name: 'optimize',
        component: () => import('@/views/OptimizeView.vue'),
        meta: { title: '简历优化' },
      },
      {
        path: 'resume-customize',
        name: 'resume-customize',
        component: () => import('@/views/ResumeCustomizeView.vue'),
        meta: { title: '简历定制' },
      },
      {
        path: 'review',
        name: 'review',
        component: () => import('@/views/ReviewView.vue'),
        meta: { title: '投递复盘' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/ProfileView.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    if (userStore.isLoggedIn && to.name === 'login') {
      return { name: 'dashboard' }
    }
    return true
  }

  if (!userStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!userStore.profileLoaded) {
    const restored = await userStore.restore()
    if (restored === false) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  return true
})

router.afterEach((to) => {
  const base = 'OfferGet'
  document.title = to.meta.title ? `${to.meta.title} · ${base}` : base
})

export default router
