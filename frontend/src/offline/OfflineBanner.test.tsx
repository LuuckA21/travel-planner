import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { apiFetch } from '../api/client'
import { m } from '../i18n/it'
import OfflineBanner from './OfflineBanner'

function renderBanner(qc = new QueryClient()) {
  render(
    <MantineProvider>
      <QueryClientProvider client={qc}>
        <OfflineBanner />
      </QueryClientProvider>
    </MantineProvider>,
  )
}
const setOnLine = (online: boolean) => {
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(online)
  act(() => void window.dispatchEvent(new Event(online ? 'online' : 'offline')))
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

test('hidden while online', () => {
  renderBanner()
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

test('banner shows last update time when offline', () => {
  const qc = new QueryClient()
  qc.setQueryData(['trips'], [], { updatedAt: new Date(2026, 9, 21, 9, 5).getTime() })
  qc.setQueryData(['trip', 't1'], {}, { updatedAt: new Date(2026, 9, 21, 14, 30).getTime() })
  renderBanner(qc)
  setOnLine(false)
  expect(screen.getByRole('status')).toHaveTextContent(m.offline.banner('14:30'))
  expect(m.offline.banner('14:30')).toBe('Sei offline · dati aggiornati alle 14:30')
  setOnLine(true)
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

test('a network error counts as offline until the next successful request', async () => {
  renderBanner()
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
  await act(() => apiFetch('/trips').catch(() => {}))
  expect(screen.getByRole('status')).toHaveTextContent(m.offline.banner())

  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]')))
  await act(() => apiFetch('/trips'))
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})
