import { defineStore } from 'pinia'

import { fetchMe, login as loginApi, register as registerApi } from '@/api/auth'
import {
  clearAuth,
  readToken,
  readUser,
  writeToken,
  writeUser,
} from '@/utils/authStorage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: readToken(),
    user: readUser(),
    profileLoaded: Boolean(readUser()),
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    displayName: (state) => state.user?.nickname || state.user?.username || '同学',
    graduationYear: (state) => state.user?.graduationYear || 2027,
  },

  actions: {
    applyAuth(data) {
      this.token = data.token
      this.user = data.user
      this.profileLoaded = true
      writeToken(data.token)
      writeUser(data.user)
    },

    updateUser(user) {
      this.user = user
      writeUser(user)
    },

    async login(payload) {
      const data = await loginApi(payload)
      this.applyAuth(data)
    },

    async register(payload) {
      const data = await registerApi(payload)
      this.applyAuth(data)
    },

    /**
     * 刷新页面后向后端确认 Token 是否有效。
     * true = 有效，false = 已失效，null = 网络不可达（保留本地登录态）
     */
    async restore() {
      if (!this.token) {
        return false
      }
      try {
        const user = await fetchMe()
        this.user = user
        this.profileLoaded = true
        writeUser(user)
        return true
      }
      catch (error) {
        if (error.httpStatus === 401) {
          this.logout()
          return false
        }
        return null
      }
    },

    logout() {
      this.token = null
      this.user = null
      this.profileLoaded = false
      clearAuth()
    },
  },
})
