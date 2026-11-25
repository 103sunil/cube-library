import { API_BASE } from '../config'

const TOKEN_KEY = 'cube.token'

export const tokenStore = {
  get() {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* storage unavailable: the session lasts for this page only */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

let unauthorizedHandler = null

/** Called whenever an authenticated request comes back 401 (expired, logged out, deactivated). */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export class ApiError extends Error {
  constructor(status, message, fieldErrors = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

function defaultMessage(status) {
  if (status === 401) return 'Your session has expired. Please log in again.'
  if (status === 403) return 'You do not have permission to do that.'
  if (status === 404) return 'The item was not found. It may have been removed.'
  if (status === 413) return 'The upload is too large. Each file must be 10 MB or smaller.'
  if (status >= 500) return 'The server had a problem. Please try again.'
  return `Request failed (${status}).`
}

async function toApiError(response) {
  const body = await response.json().catch(() => null)
  const fieldErrors = body && typeof body.fieldErrors === 'object' ? body.fieldErrors : null
  let message = (body && body.message) || defaultMessage(response.status)
  if (fieldErrors && message === 'Validation failed') {
    message = Object.values(fieldErrors).join('. ')
  }
  return new ApiError(response.status, message, fieldErrors)
}

/**
 * Calls the Cube API. JSON bodies are serialised automatically; FormData is sent as-is.
 * Errors are thrown as ApiError carrying the backend's message.
 * Pass raw: true to get the Response back (binary downloads).
 */
export async function request(path, { method = 'GET', body, headers = {}, raw = false, auth = true, signal } = {}) {
  const finalHeaders = { Accept: 'application/json', ...headers }
  const token = tokenStore.get()
  if (auth && token) {
    finalHeaders.Authorization = `Bearer ${token}`
  }
  let payload = body
  if (body !== undefined && !(body instanceof FormData)) {
    finalHeaders['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  let response
  try {
    response = await fetch(API_BASE + path, { method, headers: finalHeaders, body: payload, signal })
  } catch (error) {
    if (error && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Cannot reach the server. Check your network connection and try again.')
  }

  if (response.status === 401 && auth && token && unauthorizedHandler) {
    unauthorizedHandler()
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (raw) return response
  if (response.status === 204) return null
  const contentType = response.headers.get('content-type') || ''
  return contentType.includes('application/json') ? response.json() : response.text()
}
