import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { Booking } from '../api/types'
import { m } from '../i18n/it'
import BookingForm from './BookingForm'

const t = m.bookings
const fetchMock = vi.fn()
const onDone = vi.fn()

const existing: Booking = {
  id: 'b1', type: 'ACCOMMODATION', title: 'Hotel Tokyo', provider: 'Booking', reference: 'ABC123',
  startAt: '2027-03-10T14:00:00', endAt: '2027-03-12T10:00:00', origin: null, location: 'Shibuya', notes: null,
}

function renderForm(booking?: Booking) {
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <BookingForm tripId="t1" booking={booking} onDone={onDone} />
      </QueryClientProvider>
    </MantineProvider>,
  )
}

const label = (text: string) => screen.getByLabelText(new RegExp(`^${text}`))
const lastCall = () => {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  return { url, method: init.method, body: init.body ? JSON.parse(init.body) : undefined }
}

beforeEach(() => {
  fetchMock.mockReset().mockResolvedValue(new Response('{}', { status: 201 }))
  onDone.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

test('submits local date-times unchanged', async () => {
  const user = userEvent.setup()
  renderForm()
  await user.type(label(t.title), 'Volo Roma-Tokyo')
  await user.type(label(t.startAt), '2027-03-10T10:00')
  await user.type(label(t.endAt), '2027-03-11T06:30')
  await user.click(screen.getByRole('button', { name: t.save }))
  await waitFor(() => expect(onDone).toHaveBeenCalled())
  expect(lastCall()).toEqual({
    url: '/api/trips/t1/bookings',
    method: 'POST',
    body: {
      type: 'FLIGHT', title: 'Volo Roma-Tokyo', provider: null, reference: null,
      startAt: '2027-03-10T10:00', endAt: '2027-03-11T06:30', origin: null, location: null, notes: null,
    },
  })
})

test('shows field error from server', async () => {
  const user = userEvent.setup()
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ errors: { endAt: 'deve essere dopo l’inizio' } }), { status: 400 }))
  renderForm()
  await user.type(label(t.title), 'Volo')
  await user.type(label(t.startAt), '2027-03-10T10:00')
  await user.type(label(t.endAt), '2027-03-09T10:00')
  await user.click(screen.getByRole('button', { name: t.save }))
  expect(await screen.findByText(m.common.endBeforeStart)).toBeInTheDocument()
  expect(screen.queryByText('deve essere dopo l’inizio')).not.toBeInTheDocument()
  expect(onDone).not.toHaveBeenCalled()
  expect(label(t.title)).toHaveValue('Volo')
})

test('shows a generic error when saving fails without field errors', async () => {
  const user = userEvent.setup()
  fetchMock.mockRejectedValue(new TypeError('offline'))
  renderForm()
  await user.type(label(t.title), 'Volo')
  await user.type(label(t.startAt), '2027-03-10T10:00')
  await user.click(screen.getByRole('button', { name: t.save }))
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.networkError)
})

test('edit prefills date-times without seconds and PUTs the changes', async () => {
  const user = userEvent.setup()
  fetchMock.mockResolvedValue(new Response('{}', { status: 200 }))
  renderForm(existing)
  expect(label(t.startAt)).toHaveValue('2027-03-10T14:00')
  expect(label(t.endAt)).toHaveValue('2027-03-12T10:00')
  await user.clear(label(t.title))
  await user.type(label(t.title), 'Hotel Shinjuku')
  await user.click(screen.getByRole('button', { name: t.save }))
  await waitFor(() => expect(onDone).toHaveBeenCalled())
  expect(lastCall()).toEqual({
    url: '/api/bookings/b1',
    method: 'PUT',
    body: {
      type: 'ACCOMMODATION', title: 'Hotel Shinjuku', provider: 'Booking', reference: 'ABC123',
      startAt: '2027-03-10T14:00', endAt: '2027-03-12T10:00', origin: null, location: 'Shibuya', notes: null,
    },
  })
})

test('delete asks for confirmation then calls DELETE', async () => {
  const user = userEvent.setup()
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  renderForm(existing)
  await user.click(screen.getByRole('button', { name: t.delete }))
  expect(fetchMock).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: t.deleteConfirm }))
  await waitFor(() => expect(onDone).toHaveBeenCalled())
  expect(lastCall()).toMatchObject({ url: '/api/bookings/b1', method: 'DELETE' })
})

test('shows an error when deleting fails and stays open', async () => {
  const user = userEvent.setup()
  fetchMock.mockRejectedValue(new TypeError('offline'))
  renderForm(existing)
  await user.click(screen.getByRole('button', { name: t.delete }))
  await user.click(screen.getByRole('button', { name: t.deleteConfirm }))
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.networkError)
  expect(onDone).not.toHaveBeenCalled()
})
