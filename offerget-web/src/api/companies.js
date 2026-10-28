import request from '@/api/request'

export function fetchCompanies(params) {
  return request.get('/companies', { params })
}

export function importCompany(id) {
  return request.post(`/companies/${id}/import`)
}
