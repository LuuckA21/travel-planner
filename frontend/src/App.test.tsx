import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import App from './App'
import { m } from './i18n/it'
import { queryClient } from './queryClient'

afterEach(() => {
  vi.unstubAllGlobals()
  queryClient.clear()
})

test('renders the dashboard heading when signed in', async () => {
  const me = { id: '1', email: 'a@b.it', displayName: 'Ada' }
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => Promise.resolve(new Response(JSON.stringify(url === '/api/trips' ? [] : me), { status: 200 }))),
  )
  render(<App />)
  expect(await screen.findByRole('heading', { name: m.dashboard.title })).toBeInTheDocument()
})
