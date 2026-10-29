import request from '@/api/request'

const CREATE_TIMEOUT = 120000
const ANSWER_TIMEOUT = 120000
const FINISH_TIMEOUT = 180000
const REVERSE_TIMEOUT = 90000

export function createMockInterview(payload) {
  return request.post('/mock-interviews', payload, { timeout: CREATE_TIMEOUT })
}

export function fetchMockInterviews(jobId) {
  return request.get('/mock-interviews', { params: jobId ? { jobId } : {} })
}

export function fetchMockInterview(id) {
  return request.get(`/mock-interviews/${id}`)
}

export function answerMockInterview(id, payload) {
  return request.post(`/mock-interviews/${id}/answers`, payload, { timeout: ANSWER_TIMEOUT })
}

export function reverseMockInterview(id, payload) {
  return request.post(`/mock-interviews/${id}/reverse`, payload, { timeout: REVERSE_TIMEOUT })
}

export function finishMockInterview(id) {
  return request.post(`/mock-interviews/${id}/finish`, null, { timeout: FINISH_TIMEOUT })
}

export function abandonMockInterview(id) {
  return request.post(`/mock-interviews/${id}/abandon`)
}
