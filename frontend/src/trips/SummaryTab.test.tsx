import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import TripPage from './TripPage'

const trip = (startDate: string, endDate: string, extra: Partial<TripDetail> = {}): TripDetail => ({
  id: 't1',
  name: 'Giappone',
  destinations: ['Tokyo', 'Kyoto'],
  startDate,
  endDate,
  coverUrl: null,
  activities: [],
  bookings: [],
  documents: [],
  checklists: [],
  ...extra,
})

const queryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

function renderTrip(t: TripDetail, path = '/trips/t1', client = queryClient()) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(t), { status: 200 })))
  render(
    <MantineProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/trips/:id/:tab?" element={<TripPage />} />
          </Routes>
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

test('shows "tra 12 giorni" before the trip', async () => {
  renderTrip(trip('2026-11-02', '2026-11-10'))
  expect(await screen.findByText('tra 12 giorni')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Giappone' })).toBeInTheDocument()
  expect(screen.getByText('Tokyo · Kyoto')).toBeInTheDocument()
  expect(screen.getByText('2 nov 2026 – 10 nov 2026')).toBeInTheDocument()
})

test('shows "Giorno 3 di 10" during the trip', async () => {
  renderTrip(trip('2026-10-19', '2026-10-28'))
  expect(await screen.findByText('Giorno 3 di 10')).toBeInTheDocument()
})

test('summary lists next activities and next booking', async () => {
  renderTrip(
    trip('2026-10-19', '2026-10-28', {
      activities: [
        { id: 'a1', date: '2026-10-20', position: 0, title: 'Già fatta', startTime: null, location: null, mapsUrl: null, mapsLink: null, notes: null },
        { id: 'a2', date: '2026-10-22', position: 0, title: 'Tempio Senso-ji', startTime: '09:00', location: null, mapsUrl: null, mapsLink: null, notes: null },
      ],
      bookings: [
        { id: 'b1', type: 'FLIGHT', title: 'Volo passato', provider: null, reference: null, startAt: '2026-10-19T08:00:00', endAt: null, origin: null, location: null, notes: null },
        { id: 'b2', type: 'ACCOMMODATION', title: 'Hotel Shinjuku', provider: null, reference: null, startAt: '2026-10-23T15:00:00', endAt: null, origin: null, location: null, notes: null },
      ],
    }),
  )
  expect(await screen.findByText('Tempio Senso-ji')).toBeInTheDocument()
  expect(screen.getByText('Hotel Shinjuku')).toBeInTheDocument()
  expect(screen.queryByText('Già fatta')).not.toBeInTheDocument()
  expect(screen.queryByText('Volo passato')).not.toBeInTheDocument()
})

test('tab bar links to every tab and the edit link is in the header', async () => {
  renderTrip(trip('2026-11-02', '2026-11-10'), '/trips/t1/bookings')
  expect(await screen.findByRole('link', { name: m.trip.tabs.itinerary })).toHaveAttribute('href', '/trips/t1/itinerary')
  expect(screen.getByRole('link', { name: m.trip.tabs.summary })).toHaveAttribute('href', '/trips/t1/summary')
  expect(screen.getByRole('link', { name: m.trip.tabs.bookings })).toHaveAttribute('aria-current', 'page')
  expect(screen.getByRole('link', { name: m.dashboard.edit })).toHaveAttribute('href', '/trips/t1/edit')
})

test('keeps showing the trip when a background refetch fails', async () => {
  const client = queryClient()
  renderTrip(trip('2026-11-02', '2026-11-10'), '/trips/t1', client)
  expect(await screen.findByRole('heading', { name: 'Giappone' })).toBeInTheDocument()
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  await client.refetchQueries({ queryKey: ['trip', 't1'] })
  expect(client.getQueryState(['trip', 't1'])?.status).toBe('error')
  expect(screen.getByRole('heading', { name: 'Giappone' })).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('marks the summary tab active on /trips/:id without a tab', async () => {
  renderTrip(trip('2026-11-02', '2026-11-10'))
  expect(await screen.findByRole('link', { name: m.trip.tabs.summary })).toHaveAttribute('aria-current', 'page')
  expect(screen.getByRole('link', { name: m.trip.tabs.itinerary })).not.toHaveAttribute('aria-current')
})

test('omits the day number for items outside the trip dates', async () => {
  const a = { position: 0, startTime: null, location: null, mapsUrl: null, mapsLink: null, notes: null }
  renderTrip(
    trip('2026-10-19', '2026-10-28', {
      activities: [
        { ...a, id: 'in', date: '2026-10-22', title: 'Dentro' },
        { ...a, id: 'out', date: '2026-10-30', title: 'Fuori' },
      ],
    }),
  )
  expect(await screen.findByText('Gio 22 ott · Giorno 4')).toBeInTheDocument()
  expect(screen.getByText('Ven 30 ott')).toBeInTheDocument()
})
