import { MantineProvider } from '@mantine/core'
import { onlineManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { m } from '../i18n/it'
import type { Me } from '../api/types'
import RequireAuth from './RequireAuth'
import { clearOfflineData, claimOfflineData } from '../offline/persistence'
import { browser, logout } from './useMe'

const me: Me = { id: '1', email: 'a@b.it', displayName: 'Ada' }

function renderProtected(queryClient: QueryClient) {
  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/secret']}>
          <Routes>
            <Route path="/login" element={<p>login page</p>} />
            <Route element={<RequireAuth />}>
              <Route path="/secret" element={<p>secret content</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
}

function seeded() {
  const queryClient = new QueryClient()
  queryClient.setQueryData(['me'], me)
  queryClient.setQueryData(['trips'], [{ id: 't1' }])
  return queryClient
}

const settled = (queryClient: QueryClient) =>
  vi.waitFor(() => {
    const state = queryClient.getQueryState(['me'])
    expect(state?.fetchStatus).toBe('idle')
    expect(state?.status).toBe('error')
  })

afterEach(async () => {
  vi.restoreAllMocks()
  await clearOfflineData(new QueryClient())
  vi.unstubAllGlobals()
  onlineManager.setOnline(true)
})

test('redirects to login on 401', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))
  renderProtected(new QueryClient())
  expect(await screen.findByText('login page')).toBeInTheDocument()
})

test('renders children when offline with cached user', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  const queryClient = seeded()
  renderProtected(queryClient)
  await settled(queryClient)
  expect(screen.getByText('secret content')).toBeInTheDocument()
  expect(screen.queryByText('login page')).not.toBeInTheDocument()
})

test('network error does not clear cached data', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  const queryClient = seeded()
  renderProtected(queryClient)
  await settled(queryClient)
  expect(screen.getByText('secret content')).toBeInTheDocument()
  expect(queryClient.getQueryData(['trips'])).toEqual([{ id: 't1' }])
  expect(queryClient.getQueryData(['me'])).toEqual(me)
})

test.each([500, 502])('a %i with cached user does not redirect', async (status) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status })))
  const queryClient = seeded()
  renderProtected(queryClient)
  await settled(queryClient)
  expect(screen.getByText('secret content')).toBeInTheDocument()
  expect(screen.queryByText('login page')).not.toBeInTheDocument()
  expect(queryClient.getQueryData(['trips'])).toEqual([{ id: 't1' }])
})

test('a 401 wins over the cached user and clears cached data', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 401 }))
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = seeded()
  renderProtected(queryClient)
  expect(await screen.findByText('login page')).toBeInTheDocument()
  await vi.waitFor(() => expect(queryClient.getQueryData(['trips'])).toBeUndefined())
  expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('offline with no cache shows the network error, not an endless loader', async () => {
  onlineManager.setOnline(false)
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  renderProtected(new QueryClient())
  expect(await screen.findByText(m.common.networkError)).toBeInTheDocument()
})

test.each([
  ['succeeds', new Response(null, { status: 204 })],
  ['returns 401', new Response('{}', { status: 401 })],
])('logout clears the cache and goes to login when the request %s', async (_, response) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
  const queryClient = seeded()
  const navigate = vi.fn()
  await logout(queryClient, navigate)
  expect(queryClient.getQueryData(['trips'])).toBeUndefined()
  expect(navigate).toHaveBeenCalledWith('/login')
})

test('logout on a network error keeps the session and rethrows', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  const queryClient = seeded()
  const navigate = vi.fn()
  await expect(logout(queryClient, navigate)).rejects.toThrow()
  expect(queryClient.getQueryData(['trips'])).toEqual([{ id: 't1' }])
  expect(navigate).not.toHaveBeenCalled()
})

test('a restored snapshot of another user is never shown: content waits for the server and the claim', async () => {
  const reload = vi.spyOn(browser, 'reload').mockImplementation(() => {})
  const queryClient = seeded() // restored: user 1 and their trips
  await claimOfflineData(queryClient, me.id)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...me, id: '2' }), { status: 200 })))
  let shown = false
  const observer = new MutationObserver(() => (shown ||= document.body.textContent?.includes('secret content') ?? false))
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })

  renderProtected(queryClient)
  await vi.waitFor(() => expect(reload).toHaveBeenCalled())
  observer.disconnect()

  expect(shown).toBe(false)
  expect(queryClient.getQueryData(['trips'])).toBeUndefined()
})

test('the same user is shown once the server confirms', async () => {
  const queryClient = seeded()
  await claimOfflineData(queryClient, me.id)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(me), { status: 200 })))
  renderProtected(queryClient)
  expect(await screen.findByText('secret content')).toBeInTheDocument()
  expect(queryClient.getQueryData(['trips'])).toEqual([{ id: 't1' }])
})
