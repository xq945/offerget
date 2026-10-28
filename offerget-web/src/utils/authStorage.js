const TOKEN_KEY = 'offerget_token'
const USER_KEY = 'offerget_user'

export function readToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function writeToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function readUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  }
  catch {
    return null
  }
}

export function writeUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
