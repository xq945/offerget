import axios from 'axios'

import { clearAuth, readToken } from '@/utils/authStorage'

/**
 * Axios 实例与统一响应处理。
 * 后端响应结构 { code, message, data, timestamp }，成功直接返回 data。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

export const LOCAL_ERROR_CODE = -1

const CREDENTIAL_ENDPOINTS = ['/auth/login', '/auth/register']

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

function isCredentialRequest(url = '') {
  return CREDENTIAL_ENDPOINTS.some((endpoint) => url.includes(endpoint))
}

function normalizeError(error) {
  const response = error.response

  if (response && response.data && typeof response.data.code === 'number') {
    return {
      code: response.data.code,
      message: response.data.message,
      data: response.data.data,
      httpStatus: response.status,
    }
  }

  if (response) {
    return {
      code: LOCAL_ERROR_CODE,
      message: `请求失败（HTTP ${response.status}）`,
      data: null,
      httpStatus: response.status,
    }
  }

  if (error.code === 'ECONNABORTED') {
    return {
      code: LOCAL_ERROR_CODE,
      message: '请求超时，请稍后重试',
      data: null,
      httpStatus: null,
    }
  }

  return {
    code: LOCAL_ERROR_CODE,
    message: '无法连接服务器，请确认后端服务已启动',
    data: null,
    httpStatus: null,
  }
}

request.interceptors.request.use((config) => {
  const token = readToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && typeof body.code === 'number') {
      if (body.code === 0) {
        return body.data
      }
      return Promise.reject(normalizeError({ response: { ...response, data: body } }))
    }
    return body
  },
  (error) => {
    const apiError = normalizeError(error)

    if (apiError.httpStatus === 401 && !isCredentialRequest(error.config?.url)) {
      clearAuth()
      unauthorizedHandler?.(apiError)
    }

    return Promise.reject(apiError)
  },
)

export default request
