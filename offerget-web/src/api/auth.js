import request from '@/api/request'

export function login(payload) {
  return request.post('/auth/login', payload)
}

export function register(payload) {
  return request.post('/auth/register', payload)
}

export function fetchMe() {
  return request.get('/auth/me')
}
