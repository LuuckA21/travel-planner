import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { Checklist, TripDetail } from '../api/types'
import { m } from '../i18n/it'
import ChecklistsTab from './ChecklistsTab'

const t = m.checklists
const list = (extra: Partial<Checklist> = {}): Checklist => ({
  id: 'c1', name: 'Valigia', position: 0,
  items: [
    { id: 'i1', text: 'Passaporto', done: true, position: 0 },
    { id: 'i2', text: 'Caricabatterie', done: false, position: 1 },
    { id: 'i3', text: 'Adattatore', done: false, position: 2 },
  ],
  ...extra,
})
const trip = (checklists: Checklist[]): TripDetail => ({
  id: 't1', name: 'Giappone', destinations: [], startDate: '2027-03-10', endDate: '2027-03-12', coverUrl: null,
  activities: [], bookings: [], documents: [], checklists,
})

const fetchMock = vi.fn()
let qc: QueryClient
let refetch: () => Promise<TripDetail> = () => Promise.reject(new Error('unused'))
// Like TripPage: the tab renders whatever the trip query cache holds, so optimistic updates are visible.
function Live({ id }: { id: string }) {
  const { data } = useQuery<TripDetail>({ queryKey: ['trip', id], queryFn: () => refetch(), staleTime: Infinity })
  return <ChecklistsTab trip={data as TripDetail} />
}
const renderTab = (data: TripDetail) => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  qc.setQueryData(['trip', data.id], data)
  return render(
    <MantineProvider>
      <QueryClientProvider client={qc}>
        <Live id={data.id} />
      </QueryClientProvider>
    </MantineProvider>,
  )
}
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const call = (n = 0) => ({ url: fetchMock.mock.calls[n][0], method: fetchMock.mock.calls[n][1].method, body: JSON.parse(fetchMock.mock.calls[n][1].body ?? 'null') })

beforeEach(() => {
  fetchMock.mockReset()
  refetch = () => Promise.reject(new Error('unused'))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

test('shows an empty state', () => {
  renderTab(trip([]))
  expect(screen.getByText(t.empty)).toBeInTheDocument()
})

test('shows 1/3 progress', () => {
  renderTab(trip([list()]))
  expect(screen.getByText('1/3')).toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: 'Passaporto' })).toBeChecked()
  expect(screen.getByRole('checkbox', { name: 'Caricabatterie' })).not.toBeChecked()
})

test('toggling sends PATCH and updates optimistically', async () => {
  fetchMock.mockReturnValue(new Promise(() => {})) // never settles: only the optimistic state is visible
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('checkbox', { name: 'Caricabatterie' }))
  expect(screen.getByRole('checkbox', { name: 'Caricabatterie' })).toBeChecked()
  expect(await screen.findByText('2/3')).toBeInTheDocument()
  expect(call()).toEqual({ url: '/api/checklist-items/i2', method: 'PATCH', body: { done: true } })
})

test('a failed toggle rolls back and shows an Italian error', async () => {
  fetchMock.mockResolvedValue(json({}, 500))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('checkbox', { name: 'Caricabatterie' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.genericError)
  expect(screen.getByRole('checkbox', { name: 'Caricabatterie' })).not.toBeChecked()
  expect(screen.getByText('1/3')).toBeInTheDocument()
})

test('enter in quick-add posts the trimmed item, clears the input and keeps focus', async () => {
  fetchMock.mockResolvedValue(json({ id: 'i4', text: 'Crema', done: false, position: 3 }, 201))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  const input = screen.getByRole('textbox', { name: `${t.newItem} Valigia` })
  await user.type(input, '  Crema  {Enter}')
  await vi.waitFor(() => expect(input).toHaveValue(''))
  expect(call()).toEqual({ url: '/api/checklists/c1/items', method: 'POST', body: { text: 'Crema' } })
  expect(input).toHaveFocus()
})

test('quick-add ignores blank text', async () => {
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.type(screen.getByRole('textbox', { name: `${t.newItem} Valigia` }), '   {Enter}')
  expect(fetchMock).not.toHaveBeenCalled()
})

test('quick-add failure keeps the text and shows an error', async () => {
  fetchMock.mockResolvedValue(json({}, 500))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  const input = screen.getByRole('textbox', { name: `${t.newItem} Valigia` })
  await user.type(input, 'Crema{Enter}')
  expect(await screen.findByRole('alert')).toHaveTextContent(m.common.genericError)
  expect(input).toHaveValue('Crema')
})

test('edits an item text inline', async () => {
  fetchMock.mockResolvedValue(json({ id: 'i2', text: 'Power bank', done: false, position: 1 }))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('button', { name: `${t.editItem} Caricabatterie` }))
  const input = screen.getByRole('textbox', { name: t.editItem })
  await user.clear(input)
  await user.type(input, ' Power bank {Enter}')
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(call()).toEqual({ url: '/api/checklist-items/i2', method: 'PATCH', body: { text: 'Power bank' } })
})

test('deletes an item', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('button', { name: `${t.deleteItem} Adattatore` }))
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(call()).toEqual({ url: '/api/checklist-items/i3', method: 'DELETE', body: null })
})

test('adds a checklist', async () => {
  fetchMock.mockResolvedValue(json({ id: 'c2', name: 'Documenti', position: 1, items: [] }, 201))
  const user = userEvent.setup()
  renderTab(trip([]))
  await user.type(screen.getByRole('textbox', { name: t.newList }), ' Documenti {Enter}')
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(call()).toEqual({ url: '/api/trips/t1/checklists', method: 'POST', body: { name: 'Documenti' } })
})

test('renames a checklist', async () => {
  fetchMock.mockResolvedValue(json({ id: 'c1', name: 'Zaino', position: 0, items: [] }))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('button', { name: `${t.rename} Valigia` }))
  const input = screen.getByRole('textbox', { name: `${t.rename} Valigia` })
  await user.clear(input)
  await user.type(input, 'Zaino{Enter}')
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(call()).toEqual({ url: '/api/checklists/c1', method: 'PUT', body: { name: 'Zaino' } })
})

test('checklist delete needs a second click, then calls the API', async () => {
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('button', { name: `${t.delete} Valigia` }))
  expect(fetchMock).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: `${t.deleteConfirm} Valigia` }))
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled())
  expect(call()).toEqual({ url: '/api/checklists/c1', method: 'DELETE', body: null })
})

test('shows an Italian error when a checklist delete fails', async () => {
  fetchMock.mockRejectedValue(new TypeError('offline'))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('button', { name: `${t.delete} Valigia` }))
  await user.click(screen.getByRole('button', { name: `${t.deleteConfirm} Valigia` }))
  const alert = await screen.findByRole('alert')
  expect(within(alert).getByText(m.common.networkError)).toBeInTheDocument()
})

test('a successful retry clears the error from a failed toggle', async () => {
  fetchMock.mockResolvedValueOnce(json({}, 500)).mockResolvedValue(json({ id: 'i2', text: 'Caricabatterie', done: true, position: 1 }))
  const user = userEvent.setup()
  renderTab(trip([list()]))
  await user.click(screen.getByRole('checkbox', { name: 'Caricabatterie' }))
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  await user.click(screen.getByRole('checkbox', { name: 'Caricabatterie' }))
  await vi.waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
})

test('a refetch after the first of two quick toggles does not revert the second', async () => {
  const resolvers: (() => void)[] = []
  fetchMock.mockImplementation(() => new Promise((resolve) => resolvers.push(() => resolve(json({})))))
  const serverState = trip([list()])
  refetch = vi.fn(() => Promise.resolve(serverState))
  const user = userEvent.setup()
  renderTab(serverState)
  await user.click(screen.getByRole('checkbox', { name: 'Caricabatterie' }))
  await user.click(screen.getByRole('checkbox', { name: 'Adattatore' }))
  resolvers[0]()
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
  await new Promise((r) => setTimeout(r, 50))
  expect(refetch).not.toHaveBeenCalled()
  expect(screen.getByRole('checkbox', { name: 'Adattatore' })).toBeChecked()
  resolvers[1]()
  await vi.waitFor(() => expect(refetch).toHaveBeenCalledTimes(1))
})
