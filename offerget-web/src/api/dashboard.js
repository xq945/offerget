import request from '@/api/request'

export function fetchOverview() {
  return request.get('/dashboard/overview')
}

export function fetchCalendar(year, month) {
  return request.get('/dashboard/calendar', { params: { year, month } })
}
