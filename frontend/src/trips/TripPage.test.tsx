import { MantineProvider } from '@mantine/core'
import { onlineManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import TripPage from './TripPage'

const trip: TripDetail = {
  id: 't1', name: 'Giappone', destinations: [], startDate: '2027-03-10', endDate: '2027-03-12', coverUrl: null,
  activities: [], bookings: [], documents: [], checklists: [],
}

function renderTrip() {
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/trips/t1/summary']}>
          <Routes>
            <Route path="/trips/:id/:tab?" element={<TripPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
  onlineManager.setOnline(true)
})

test('has a link back to the dashboard', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(trip), { status: 200 })))
  renderTrip()
  expect(await screen.findByRole('link', { name: new RegExp(m.trip.back) })).toHaveAttribute('href', '/')
})

test('a trip never cached says it is not available offline instead of loading forever', async () => {
  onlineManager.setOnline(false)
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  renderTrip()
  expect(await screen.findByText(m.common.unavailableOffline)).toBeInTheDocument()
})
