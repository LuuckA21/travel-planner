import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { Activity, TripDetail } from '../api/types'
import { m } from '../i18n/it'
import ItineraryTab from './ItineraryTab'

const act = (id: string, date: string, position: number, extra: Partial<Activity> = {}): Activity => ({
  id, date, position, title: `Attività ${id}`, startTime: null, location: null, mapsUrl: null, mapsLink: null, notes: null, ...extra,
})

const trip = (extra: Partial<TripDetail> = {}): TripDetail => ({
  id: 't1',
  name: 'Giappone',
  destinations: [],
  startDate: '2027-03-10',
  endDate: '2027-03-12',
  coverUrl: null,
  activities: [],
  bookings: [],
  documents: [],
  checklists: [],
  ...extra,
})

const fetchMock = vi.fn()
const scrollIntoView = vi.fn()

function renderTab(t: TripDetail, path = '/trips/t1/itinerary') {
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={[path]}>
          <ItineraryTab trip={t} />
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
}

const lastCall = () => {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  return { url, method: init.method, body: init.body ? JSON.parse(init.body) : undefined }
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 9, 21, 12, 0))
  fetchMock.mockReset().mockResolvedValue(new Response(null, { status: 204 }))
  scrollIntoView.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  Element.prototype.scrollIntoView = scrollIntoView
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

test('renders a section per day with header', () => {
  renderTab(trip({ activities: [act('a', '2027-03-11', 1, { startTime: '09:30:00', location: 'Shibuya', notes: 'Portare contanti' })] }))
  expect(screen.getByRole('heading', { name: 'Mer 10 mar · Giorno 1' }).closest('section')).toHaveAttribute('id', 'day-2027-03-10')
  const day2 = screen.getByRole('heading', { name: 'Gio 11 mar · Giorno 2' }).closest('section')!
  expect(day2).toHaveAttribute('id', 'day-2027-03-11')
  expect(within(day2).getByText('Attività a')).toBeInTheDocument()
  expect(within(day2).getByText('09:30')).toBeInTheDocument()
  expect(within(day2).getByText('Shibuya')).toBeInTheDocument()
  expect(within(day2).getByText('Portare contanti')).toBeInTheDocument()
  expect(document.getElementById('day-2027-03-12')).toBeInTheDocument()
})

test('maps link opens in a new tab', () => {
  renderTab(trip({ activities: [act('a', '2027-03-10', 1, { mapsLink: 'https://maps.example/x' })] }))
  const link = screen.getByRole('link', { name: m.itinerary.openMap })
  expect(link).toHaveAttribute('href', 'https://maps.example/x')
  expect(link).toHaveAttribute('target', '_blank')
  expect(link).toHaveAttribute('rel', 'noopener')
})

test('move down sends reordered ids for that day', async () => {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  renderTab(trip({ activities: [act('a', '2027-03-10', 1), act('b', '2027-03-10', 2), act('c', '2027-03-11', 1)] }))
  await user.click(screen.getByRole('button', { name: `${m.itinerary.moveDown}: Attività a` }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(lastCall()).toEqual({
    url: '/api/trips/t1/activities/order',
    method: 'PUT',
    body: { date: '2027-03-10', activityIds: ['b', 'a'] },
  })
})

test('first item has move-up disabled and last has move-down disabled', () => {
  renderTab(trip({ activities: [act('a', '2027-03-10', 1), act('b', '2027-03-10', 2)] }))
  expect(screen.getByRole('button', { name: `${m.itinerary.moveUp}: Attività a` })).toBeDisabled()
  expect(screen.getByRole('button', { name: `${m.itinerary.moveDown}: Attività a` })).toBeEnabled()
  expect(screen.getByRole('button', { name: `${m.itinerary.moveUp}: Attività b` })).toBeEnabled()
  expect(screen.getByRole('button', { name: `${m.itinerary.moveDown}: Attività b` })).toBeDisabled()
})

test('shows outside-dates section only when needed', () => {
  renderTab(trip({ activities: [act('in', '2027-03-10', 1)] }))
  expect(screen.queryByText(m.itinerary.outside)).not.toBeInTheDocument()
})

test('outside-dates activities are listed and stay editable', async () => {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  renderTab(trip({ activities: [act('out', '2027-03-20', 1)] }))
  const section = screen.getByRole('heading', { name: m.itinerary.outside }).closest('section')!
  expect(within(section).getByText('Attività out')).toBeInTheDocument()
  await user.click(within(section).getByRole('button', { name: `${m.itinerary.edit}: Attività out` }))
  await user.clear(await screen.findByLabelText(new RegExp(`^${m.itinerary.title}`)))
  await user.type(screen.getByLabelText(new RegExp(`^${m.itinerary.title}`)), 'Nuovo titolo')
  await user.click(screen.getByRole('button', { name: m.itinerary.save }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(lastCall()).toEqual({
    url: '/api/activities/out',
    method: 'PUT',
    body: { date: '2027-03-20', title: 'Nuovo titolo', startTime: null, location: null, mapsUrl: null, notes: null },
  })
})

test('booking rows are read-only links to the bookings tab', () => {
  renderTab(
    trip({
      bookings: [
        { id: 'late', type: 'FLIGHT', title: 'Volo B', provider: null, reference: null, startAt: '2027-03-10T18:00:00', endAt: null, origin: null, location: null, notes: null },
        { id: 'early', type: 'FLIGHT', title: 'Volo A', provider: null, reference: null, startAt: '2027-03-10T08:15:00', endAt: null, origin: null, location: null, notes: null },
      ],
    }),
  )
  const links = screen.getAllByRole('link', { name: /Volo/ })
  expect(links.map((l) => l.textContent)).toEqual(['08:15 Volo A', '18:00 Volo B'])
  expect(links[0]).toHaveAttribute('href', '/trips/t1/bookings')
})

test('add activity opens the form with the date prefilled and posts it', async () => {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  fetchMock.mockResolvedValue(new Response('{}', { status: 201 }))
  renderTab(trip())
  const day2 = screen.getByRole('heading', { name: 'Gio 11 mar · Giorno 2' }).closest('section')!
  await user.click(within(day2).getByRole('button', { name: m.itinerary.add }))
  expect(await screen.findByLabelText(new RegExp(`^${m.itinerary.date}`))).toHaveValue('2027-03-11')
  await user.type(screen.getByLabelText(new RegExp(`^${m.itinerary.title}`)), 'Cena')
  await user.type(screen.getByLabelText(m.itinerary.time), '20:30')
  await user.type(screen.getByLabelText(m.itinerary.mapsUrl), 'https://maps.example/x')
  await user.click(screen.getByRole('button', { name: m.itinerary.save }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(lastCall()).toEqual({
    url: '/api/trips/t1/activities',
    method: 'POST',
    body: { date: '2027-03-11', title: 'Cena', startTime: '20:30', location: null, mapsUrl: 'https://maps.example/x', notes: null },
  })
})

test('shows field errors from the server and keeps the form open', async () => {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ errors: { mapsUrl: 'deve iniziare con https://' } }), { status: 400 }))
  renderTab(trip())
  await user.click(screen.getAllByRole('button', { name: m.itinerary.add })[0])
  await user.type(await screen.findByLabelText(new RegExp(`^${m.itinerary.title}`)), 'Cena')
  await user.type(screen.getByLabelText(m.itinerary.mapsUrl), 'http://x')
  await user.click(screen.getByRole('button', { name: m.itinerary.save }))
  expect(await screen.findByText('deve iniziare con https://')).toBeInTheDocument()
  expect(screen.getByLabelText(new RegExp(`^${m.itinerary.title}`))).toHaveValue('Cena')
})

test('delete asks for confirmation then calls DELETE', async () => {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
  renderTab(trip({ activities: [act('a', '2027-03-10', 1)] }))
  await user.click(screen.getByRole('button', { name: `${m.itinerary.edit}: Attività a` }))
  await user.click(await screen.findByRole('button', { name: m.itinerary.delete }))
  expect(fetchMock).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: m.itinerary.deleteConfirm }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(lastCall()).toMatchObject({ url: '/api/activities/a', method: 'DELETE' })
})

test('scrolls to ?day= on mount', () => {
  renderTab(trip(), '/trips/t1/itinerary?day=2027-03-11')
  expect(scrollIntoView).toHaveBeenCalledTimes(1)
  expect(scrollIntoView.mock.contexts[0]).toBe(document.getElementById('day-2027-03-11'))
})

test('scrolls to today when the trip is in progress', () => {
  renderTab(trip({ startDate: '2026-10-20', endDate: '2026-10-23' }))
  expect(scrollIntoView.mock.contexts[0]).toBe(document.getElementById('day-2026-10-21'))
})

test('does not scroll for an upcoming trip without ?day=', () => {
  renderTab(trip())
  expect(scrollIntoView).not.toHaveBeenCalled()
})
