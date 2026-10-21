import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { TripSummary } from '../api/types'
import { m } from '../i18n/it'
import DashboardPage from './DashboardPage'

const trip = (id: string, startDate: string, endDate: string): TripSummary => ({
  id,
  name: `Trip ${id}`,
  destinations: ['Roma'],
  startDate,
  endDate,
  coverUrl: null,
})

// Unordered on purpose; the server returns trips by start date.
const trips = [
  trip('past-old', '2026-01-01', '2026-01-10'),
  trip('up-late', '2026-12-01', '2026-12-10'),
  trip('now', '2026-10-20', '2026-10-25'),
  trip('past-new', '2026-09-01', '2026-09-10'),
  trip('up-soon', '2026-11-01', '2026-11-05'),
]

function renderDashboard(list: TripSummary[] = trips) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(list), { status: 200 })))
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 21, 12, 0))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const names = (region: HTMLElement) => within(region).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)

test('groups trips into in progress, upcoming and past in the right order', async () => {
  renderDashboard()
  const inProgress = await screen.findByRole('region', { name: m.dashboard.inProgress })
  expect(names(inProgress)).toEqual(['Trip now'])
  expect(names(screen.getByRole('region', { name: m.dashboard.upcoming }))).toEqual(['Trip up-soon', 'Trip up-late'])
  expect(names(screen.getByRole('region', { name: m.dashboard.past }))).toEqual(['Trip past-new', 'Trip past-old'])
})

test('prefetches in-progress and upcoming trips for offline use, not past ones', async () => {
  renderDashboard()
  await screen.findByRole('region', { name: m.dashboard.inProgress })
  const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4))
  expect(fetchMock.mock.calls.map(([url]) => url).sort()).toEqual(['/api/trips', '/api/trips/now', '/api/trips/up-late', '/api/trips/up-soon'])
})

test('Today button links to current day', async () => {
  renderDashboard()
  const link = await screen.findByRole('link', { name: m.dashboard.today })
  expect(link).toHaveAttribute('href', '/trips/now/itinerary?day=2026-10-21')
})

test('every trip card links to its trip page', async () => {
  renderDashboard()
  const upcoming = await screen.findByRole('region', { name: m.dashboard.upcoming })
  expect(within(upcoming).getByRole('link', { name: 'Trip up-soon' })).toHaveAttribute('href', '/trips/up-soon')
  expect(within(screen.getByRole('region', { name: m.dashboard.past })).getByRole('link', { name: 'Trip past-old' })).toHaveAttribute('href', '/trips/past-old')
})

test('a failed refetch with trips on screen shows no error alert', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClient.setQueryData(['trips'], trips)
  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
  await queryClient.refetchQueries({ queryKey: ['trips'] })
  await vi.waitFor(() => expect(queryClient.getQueryState(['trips'])?.status).toBe('error'))
  await new Promise((r) => setTimeout(r, 50)) // let the error state render
  expect(screen.getByRole('region', { name: m.dashboard.upcoming })).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('empty state offers a new trip', async () => {
  renderDashboard([])
  expect(await screen.findByText(m.dashboard.empty)).toBeInTheDocument()
  expect(screen.getAllByRole('link', { name: m.dashboard.newTrip }).length).toBeGreaterThan(0)
})

test('logout on a network error shows the Italian error and stays on the dashboard', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => (url === '/api/trips' ? Promise.resolve(new Response('[]')) : Promise.reject(new TypeError('offline')))),
  )
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/login" element={<p>login page</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
  await userEvent.setup().click(await screen.findByRole('button', { name: m.auth.logout }))
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.networkError)
  expect(screen.getByRole('heading', { name: m.dashboard.title })).toBeInTheDocument()
  expect(screen.queryByText('login page')).not.toBeInTheDocument()
})
