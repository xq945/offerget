import request from '@/api/request'

export function fetchReview() {
  return request.get('/review/overview')
}
