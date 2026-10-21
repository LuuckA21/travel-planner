import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { DocumentMeta, TripDetail } from '../api/types'
import { m } from '../i18n/it'
import { formatSize } from '../lib/documents'
import DocumentsTab from './DocumentsTab'

const t = m.documents
const doc = (id: string, extra: Partial<DocumentMeta> = {}): DocumentMeta => ({
  id, bookingId: null, filename: `${id}.pdf`, contentType: 'application/pdf', sizeBytes: 2048, createdAt: '', fileUrl: `/api/documents/${id}/file`, ...extra,
})
const trip = (extra: Partial<TripDetail> = {}): TripDetail => ({
  id: 't1', name: 'Giappone', destinations: [], startDate: '2027-03-10', endDate: '2027-03-12', coverUrl: null,
  activities: [], bookings: [], documents: [], checklists: [], ...extra,
})

const fetchMock = vi.fn()
const renderTab = (data: TripDetail) =>
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
        <DocumentsTab trip={data} />
      </QueryClientProvider>
    </MantineProvider>,
  )

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

test('formats sizes as KB or MB', () => {
  expect(formatSize(500)).toBe('1 KB')
  expect(formatSize(2048)).toBe('2 KB')
  expect(formatSize(1.5 * 1024 * 1024)).toBe('1,5 MB')
})

test('shows an empty state', () => {
  renderTab(trip())
  expect(screen.getByText(t.empty)).toBeInTheDocument()
})

test('lists documents with size, booking title, open and download links', () => {
  renderTab(trip({
    bookings: [{ id: 'b1', type: 'FLIGHT', title: 'Volo Tokyo', provider: null, reference: null, startAt: '2027-03-10T10:00:00', endAt: null, origin: null, location: null, notes: null }],
    documents: [doc('d1', { bookingId: 'b1' }), doc('d2')],
  }))
  const open = screen.getByRole('link', { name: 'd1.pdf' })
  expect(open).toHaveAttribute('href', '/api/documents/d1/file')
  expect(open).toHaveAttribute('target', '_blank')
  expect(screen.getByText(/Volo Tokyo/)).toBeInTheDocument()
  expect(screen.getAllByText(/2 KB/)).toHaveLength(2)
  expect(screen.getAllByRole('link', { name: new RegExp(t.download) })[0]).toHaveAttribute('href', '/api/documents/d1/file?download=true')
})

test('make available offline stores every file and the cover, then shows the result', async () => {
  const add = vi.fn(async (url: string) => {
    if (url.includes('d2')) throw new TypeError('Failed to fetch')
  })
  const open = vi.fn(async () => ({ add }))
  vi.stubGlobal('caches', { open })
  renderTab(trip({ coverUrl: '/api/trips/t1/cover?v=3', documents: [doc('d1'), doc('d2')] }))
  await userEvent.setup().click(screen.getByRole('button', { name: t.offline }))
  expect(await screen.findByText(t.offlineDone(2, 1))).toBeInTheDocument()
  expect(open).toHaveBeenCalledWith('documents')
  expect(add.mock.calls.map(([url]) => url).sort()).toEqual(['/api/documents/d1/file', '/api/documents/d2/file', '/api/trips/t1/cover?v=3'])
})

test('delete needs a second click, then calls the API', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  const user = userEvent.setup()
  renderTab(trip({ documents: [doc('d1')] }))
  await user.click(screen.getByRole('button', { name: t.delete }))
  expect(fetchMock).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: t.deleteConfirm }))
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(fetchMock.mock.calls[0][0]).toBe('/api/documents/d1')
  expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
})

test('shows an Italian error when delete fails', async () => {
  fetchMock.mockResolvedValue(new Response('{}', { status: 500 }))
  const user = userEvent.setup()
  renderTab(trip({ documents: [doc('d1')] }))
  await user.click(screen.getByRole('button', { name: t.delete }))
  await user.click(screen.getByRole('button', { name: t.deleteConfirm }))
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.genericError)
})
