export class ApiError extends Error {
  status: number
  code?: string
  fieldErrors?: Record<string, string>

  constructor(status: number, message: string, code?: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Network request failed')
    this.name = 'NetworkError'
  }
}

function xsrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : undefined
}

async function send<T>(path: string, init: RequestInit, headers: Headers): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const token = xsrfToken()
  if (method !== 'GET' && method !== 'HEAD' && token) headers.set('X-XSRF-TOKEN', token)

  let res: Response
  try {
    res = await fetch(`/api${path}`, { ...init, headers, credentials: 'same-origin' })
  } catch {
    throw new NetworkError()
  }

  if (!res.ok) {
    const problem = await res.json().catch(() => ({}))
    throw new ApiError(res.status, problem.detail ?? res.statusText, problem.code, problem.errors)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && init.body !== null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', 'application/json')
  return send<T>(path, init, headers)
}

// The browser sets the multipart Content-Type (with boundary) itself.
export function apiUpload<T>(path: string, form: FormData, method: 'POST' | 'PUT' = 'POST'): Promise<T> {
  return send<T>(path, { method, body: form }, new Headers({ Accept: 'application/json' }))
}
