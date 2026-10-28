export const STATUS_META = {
  PENDING: { label: '待投递', tone: 'bg-slate-100 text-slate-600 ring-slate-200' },
  APPLIED: { label: '已投递', tone: 'bg-blue-50 text-blue-700 ring-blue-200' },
  WRITTEN_TEST: { label: '笔试', tone: 'bg-violet-50 text-violet-700 ring-violet-200' },
  INTERVIEW: { label: '面试', tone: 'bg-amber-50 text-amber-700 ring-amber-200' },
  OFFER: { label: 'Offer', tone: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  REJECTED: { label: '未通过', tone: 'bg-rose-50 text-rose-600 ring-rose-200' },
}

export const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待投递' },
  { value: 'APPLIED', label: '已投递' },
  { value: 'WRITTEN_TEST', label: '笔试' },
  { value: 'INTERVIEW', label: '面试' },
  { value: 'OFFER', label: 'Offer' },
  { value: 'REJECTED', label: '未通过' },
]

export const EVENT_META = {
  DEADLINE: { label: '截止', dot: 'bg-rose-400', tone: 'bg-rose-50 text-rose-600' },
  APPLY: { label: '投递', dot: 'bg-blue-400', tone: 'bg-blue-50 text-blue-600' },
  WRITTEN_TEST: { label: '笔试', dot: 'bg-violet-400', tone: 'bg-violet-50 text-violet-600' },
  INTERVIEW: { label: '面试', dot: 'bg-amber-400', tone: 'bg-amber-50 text-amber-600' },
  RESULT: { label: '结果', dot: 'bg-emerald-400', tone: 'bg-emerald-50 text-emerald-600' },
}

export function statusMeta(status) {
  return STATUS_META[status] || STATUS_META.PENDING
}

export function eventMeta(type) {
  return EVENT_META[type] || EVENT_META.APPLY
}

export function deadlineText(daysLeft) {
  if (daysLeft === null || daysLeft === undefined) {
    return '未设置截止'
  }
  if (daysLeft < 0) {
    return '已截止'
  }
  if (daysLeft === 0) {
    return '今天截止'
  }
  return `剩 ${daysLeft} 天`
}

export function deadlineTone(daysLeft) {
  if (daysLeft === null || daysLeft === undefined || daysLeft < 0) {
    return 'text-slate-400'
  }
  if (daysLeft <= 3) {
    return 'text-rose-500'
  }
  return 'text-slate-500'
}

export function formatDate(value) {
  if (!value) {
    return '—'
  }
  const text = String(value)
  return text.length >= 10 ? text.slice(5, 10).replace('-', '/') : text
}

export function formatDateFull(value) {
  if (!value) {
    return '—'
  }
  return String(value).slice(0, 10)
}

export function formatDateTime(value) {
  if (!value) {
    return '—'
  }
  const text = String(value)
  return text.length >= 16 ? `${text.slice(5, 10).replace('-', '/')} ${text.slice(11, 16)}` : text
}

export function formatFileSize(bytes) {
  if (!bytes) {
    return '—'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function scoreTone(score) {
  if (score === null || score === undefined) {
    return { text: 'text-slate-400', ring: '#cbd5e1', label: '待匹配' }
  }
  if (score >= 80) {
    return { text: 'text-emerald-600', ring: '#10b981', label: '高度匹配' }
  }
  if (score >= 65) {
    return { text: 'text-blue-600', ring: '#2563eb', label: '比较匹配' }
  }
  if (score >= 50) {
    return { text: 'text-amber-500', ring: '#f59e0b', label: '基础匹配' }
  }
  return { text: 'text-rose-500', ring: '#f43f5e', label: '匹配较低' }
}

export function greeting() {
  const hour = new Date().getHours()
  if (hour < 6) {
    return '夜深了'
  }
  if (hour < 11) {
    return '早上好'
  }
  if (hour < 14) {
    return '中午好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
}

export function companyInitial(company) {
  return (company || '?').trim().slice(0, 1)
}

const COMPANY_TONES = [
  'from-blue-500 to-sky-400',
  'from-violet-500 to-indigo-400',
  'from-emerald-500 to-teal-400',
  'from-amber-500 to-orange-400',
  'from-rose-500 to-pink-400',
  'from-cyan-500 to-blue-400',
]

export function companyTone(company) {
  const text = company || ''
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 997
  }
  return COMPANY_TONES[hash % COMPANY_TONES.length]
}
