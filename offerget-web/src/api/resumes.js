import request from '@/api/request'

export function fetchResumes() {
  return request.get('/resumes')
}

export function fetchResume(id) {
  return request.get(`/resumes/${id}`)
}

export function uploadResume(file, name, versionLabel) {
  const form = new FormData()
  form.append('file', file)
  if (name) {
    form.append('name', name)
  }
  if (versionLabel) {
    form.append('versionLabel', versionLabel)
  }
  return request.post('/resumes', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export function updateResume(id, payload) {
  return request.put(`/resumes/${id}`, payload)
}

export function setDefaultResume(id) {
  return request.put(`/resumes/${id}/default`)
}

export function deleteResume(id) {
  return request.delete(`/resumes/${id}`)
}

export function saveBuilderResume(id, payload) {
  return id
    ? request.put(`/resumes/${id}/builder`, payload)
    : request.post('/resumes/builder', payload)
}

export function fetchAutofill(id) {
  return request.get(`/resumes/${id}/autofill`)
}
