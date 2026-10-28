import request from '@/api/request'

export function fetchJobs(params) {
  return request.get('/jobs', { params })
}

export function fetchJob(id) {
  return request.get(`/jobs/${id}`)
}

export function createJob(payload) {
  return request.post('/jobs', payload)
}

export function updateJob(id, payload) {
  return request.put(`/jobs/${id}`, payload)
}

export function changeJobStatus(id, status) {
  return request.patch(`/jobs/${id}/status`, { status })
}

export function deleteJob(id) {
  return request.delete(`/jobs/${id}`)
}
