import request from '@/api/request'

export function fetchWatches() {
  return request.get('/crawl/watch-companies')
}

export function createWatch(payload) {
  return request.post('/crawl/watch-companies', payload)
}

export function updateWatch(id, payload) {
  return request.put(`/crawl/watch-companies/${id}`, payload)
}

export function deleteWatch(id) {
  return request.delete(`/crawl/watch-companies/${id}`)
}

export function discoverDomain(id) {
  return request.post(`/crawl/watch-companies/${id}/discover-domain`, null, { timeout: 60000 })
}

export function runCrawl(id) {
  return request.post(`/crawl/watch-companies/${id}/run`, null, { timeout: 180000 })
}

export function runAllCrawl() {
  return request.post('/crawl/run-all', null, { timeout: 600000 })
}

export function fetchDrafts(status = 'DRAFT') {
  return request.get('/crawl/drafts', { params: { status } })
}

export function updateDraft(id, payload) {
  return request.put(`/crawl/drafts/${id}`, payload)
}

export function approveDraft(id) {
  return request.put(`/crawl/drafts/${id}/approve`)
}

export function rejectDraft(id) {
  return request.put(`/crawl/drafts/${id}/reject`)
}

export function approveDrafts(ids) {
  return request.post('/crawl/drafts/approve-batch', { ids })
}
