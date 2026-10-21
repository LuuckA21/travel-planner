import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import type { Me } from '../api/types'
import RequireAuth from './RequireAuth'

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

afterEach(() => vi.unstubAllGlobals())

test('redirects to login on 401', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))
  renderProtected(new QueryClient())
  expect(await screen.findByText('login page')).toBeInTheDocument()
})

test('renders children when offline with cached user', async () => {
  const fetchMock = vi.fn().mockRejectedValue(new TypeError('offline'))
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = new QueryClient()
  queryClient.setQueryData(['me'], me)
  renderProtected(queryClient)
  expect(await screen.findByText('secret content')).toBeInTheDocument()
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(screen.queryByText('login page')).not.toBeInTheDocument()
})

test('network error does not clear cached data', async () => {
  const fetchMock = vi.fn().mockRejectedValue(new TypeError('offline'))
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = new QueryClient()
  queryClient.setQueryData(['me'], me)
  queryClient.setQueryData(['trips'], [{ id: 't1' }])
  renderProtected(queryClient)
  await screen.findByText('secret content')
  await vi.waitFor(() => expect(queryClient.getQueryState(['me'])?.fetchStatus).toBe('idle'))
  expect(queryClient.getQueryData(['trips'])).toEqual([{ id: 't1' }])
  expect(queryClient.getQueryData(['me'])).toEqual(me)
})
