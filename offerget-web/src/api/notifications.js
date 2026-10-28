import request from '@/api/request'

export function fetchNotifications() {
  return request.get('/notifications')
}

export function markNotificationRead(key) {
  return request.put('/notifications/read', { key })
}

export function markAllNotificationsRead() {
  return request.put('/notifications/read-all')
}
