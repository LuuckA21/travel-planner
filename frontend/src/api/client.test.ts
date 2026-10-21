import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { ApiError, NetworkError, apiFetch, apiUpload } from './client'

const fetchMock = vi.fn()

function problem(status: number, body: object) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  document.cookie = 'XSRF-TOKEN=abc'
})

afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

test('sends XSRF header from cookie on POST', async () => {
  fetchMock.mockResolvedValue(new Response('{"id":"1"}', { status: 201 }))
  await apiFetch('/trips', { method: 'POST', body: JSON.stringify({ name: 'x' }) })
  const [url, init] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/trips')
  expect(init.credentials).toBe('same-origin')
  expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('abc')
  expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
})

test('does not send XSRF header on GET', async () => {
  fetchMock.mockResolvedValue(new Response('[]', { status: 200 }))
  await expect(apiFetch('/trips')).resolves.toEqual([])
  expect(new Headers(fetchMock.mock.calls[0][1].headers).has('X-XSRF-TOKEN')).toBe(false)
})

test('throws ApiError with status, code and field errors', async () => {
  fetchMock.mockResolvedValueOnce(problem(400, { status: 400, errors: { name: 'required' } }))
  await expect(apiFetch('/trips', { method: 'POST' })).rejects.toMatchObject({
    status: 400,
    fieldErrors: { name: 'required' },
  })
  fetchMock.mockResolvedValueOnce(problem(403, { status: 403, code: 'EMAIL_NOT_VERIFIED' }))
  const err = (await apiFetch('/auth/login', { method: 'POST' }).catch((e) => e)) as ApiError
  expect(err).toBeInstanceOf(ApiError)
  expect(err.status).toBe(403)
  expect(err.code).toBe('EMAIL_NOT_VERIFIED')
})

test('throws NetworkError when fetch rejects', async () => {
  fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
  await expect(apiFetch('/trips')).rejects.toBeInstanceOf(NetworkError)
})

test('returns undefined for 204', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  await expect(apiFetch('/trips/1', { method: 'DELETE' })).resolves.toBeUndefined()
})

test.each([201, 202])('returns undefined for an empty %i', async (status) => {
  fetchMock.mockResolvedValue(new Response(null, { status }))
  await expect(apiFetch('/auth/register', { method: 'POST' })).resolves.toBeUndefined()
})

test('apiUpload sends FormData without a JSON content type', async () => {
  fetchMock.mockResolvedValue(new Response('{}', { status: 201 }))
  const form = new FormData()
  form.append('file', new Blob(['x']), 'a.pdf')
  await apiUpload('/trips/1/documents', form)
  const [, init] = fetchMock.mock.calls[0]
  expect(init.method).toBe('POST')
  expect(init.body).toBe(form)
  expect(new Headers(init.headers).has('Content-Type')).toBe(false)
  expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('abc')
})
