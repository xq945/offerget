import request from '@/api/request'

export function fetchProfile() {
  return request.get('/profile')
}

export function updateProfile(payload) {
  return request.put('/profile', payload)
}

export function changePassword(payload) {
  return request.put('/profile/password', payload)
}
