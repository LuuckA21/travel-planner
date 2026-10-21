import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { Booking, TripDetail } from '../api/types'
import { m } from '../i18n/it'
import BookingDetail from './BookingDetail'
import BookingsTab from './BookingsTab'

const t = m.bookings
const booking = (id: string, startAt: string, extra: Partial<Booking> = {}): Booking => ({
  id, type: 'FLIGHT', title: `Prenotazione ${id}`, provider: null, reference: null, startAt, endAt: null, origin: null, location: null, notes: null, ...extra,
})

const trip = (extra: Partial<TripDetail> = {}): TripDetail => ({
  id: 't1', name: 'Giappone', destinations: [], startDate: '2027-03-10', endDate: '2027-03-12', coverUrl: null,
  activities: [], bookings: [], documents: [], checklists: [], ...extra,
})

const fetchMock = vi.fn()
const wrap = (ui: React.ReactNode, path: string) => (
  <MantineProvider>
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </QueryClientProvider>
  </MantineProvider>
)

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

test('lists bookings chronologically as links to their detail page', () => {
  render(wrap(<BookingsTab trip={trip({ bookings: [booking('late', '2027-03-11T18:00:00'), booking('early', '2027-03-10T08:15:00')] })} />, '/trips/t1/bookings'))
  const links = screen.getAllByRole('link', { name: /Prenotazione/ })
  expect(links.map((l) => l.getAttribute('href'))).toEqual(['/trips/t1/bookings/early', '/trips/t1/bookings/late'])
  expect(links[0]).toHaveTextContent('08:15')
})

test('shows an empty state and an add button that opens the form', async () => {
  const user = userEvent.setup()
  render(wrap(<BookingsTab trip={trip()} />, '/trips/t1/bookings'))
  expect(screen.getByText(t.empty)).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: t.add }))
  expect(await screen.findByLabelText(new RegExp(`^${t.title}`))).toBeInTheDocument()
})

function renderDetail(data: TripDetail) {
  fetchMock.mockResolvedValue(new Response(JSON.stringify(data), { status: 200 }))
  render(wrap(
    <Routes>
      <Route path="/trips/:id/bookings/:bookingId" element={<BookingDetail />} />
      <Route path="/trips/:id/:tab?" element={<p>tab page</p>} />
    </Routes>,
    '/trips/t1/bookings/b1',
  ))
}

test('detail shows all fields and only the documents attached to this booking', async () => {
  renderDetail(trip({
    bookings: [booking('b1', '2027-03-10T10:00:00', { endAt: '2027-03-10T14:30:00', provider: 'ITA', reference: 'XYZ', origin: 'Roma', location: 'Tokyo', notes: 'Bagaglio extra' })],
    documents: [
      { id: 'd1', bookingId: 'b1', filename: 'biglietto.pdf', contentType: 'application/pdf', sizeBytes: 1, createdAt: '', fileUrl: '/files/d1' },
      { id: 'd2', bookingId: null, filename: 'altro.pdf', contentType: 'application/pdf', sizeBytes: 1, createdAt: '', fileUrl: '/files/d2' },
    ],
  }))
  expect(await screen.findByRole('heading', { name: /Prenotazione b1/ })).toBeInTheDocument()
  for (const text of ['ITA', 'XYZ', 'Roma', 'Tokyo', 'Bagaglio extra']) expect(screen.getByText(text)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'biglietto.pdf' })).toHaveAttribute('href', '/files/d1')
  expect(screen.queryByText('altro.pdf')).not.toBeInTheDocument()
})

test('detail redirects to the bookings tab when the booking does not exist', async () => {
  renderDetail(trip())
  expect(await screen.findByText('tab page')).toBeInTheDocument()
})
