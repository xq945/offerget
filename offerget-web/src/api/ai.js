import request from '@/api/request'

export function runMatch(payload) {
  return request.post('/ai/match', payload)
}

export function fetchMatchHistory(jobId) {
  return request.get('/ai/matches', { params: jobId ? { jobId } : {} })
}

export function fetchLatestMatch(jobId) {
  return request.get('/ai/matches/latest', { params: { jobId } })
}

export function runOptimize(payload) {
  return request.post('/ai/optimize', payload)
}

export function fetchOptimizeHistory(jobId) {
  return request.get('/ai/optimizations', { params: jobId ? { jobId } : {} })
}

export function runCustomize(payload) {
  return request.post('/ai/customize', payload)
}

export function fetchCustomizeHistory(jobId) {
  return request.get('/ai/customizations', { params: jobId ? { jobId } : {} })
}

export function applyCustomization(id, payload) {
  return request.put(`/ai/customizations/${id}/apply`, payload)
}

export function fetchAiConfig() {
  return request.get('/ai/config')
}
