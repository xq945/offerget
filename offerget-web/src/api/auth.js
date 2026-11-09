import request from '@/api/request'

export function login(payload) {
  return request.post('/auth/login', payload)
}

export function register(payload) {
  return request.post('/auth/register', payload)
}

export function sendEmailCode(payload) {
  return request.post('/auth/email-code', payload)
}

export function loginByEmailCode(payload) {
  return request.post('/auth/login/email', payload)
}

export function fetchMe() {
  return request.get('/auth/me')
}
