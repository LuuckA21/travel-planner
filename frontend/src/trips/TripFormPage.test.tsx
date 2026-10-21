import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { m } from '../i18n/it'
import TripFormPage from './TripFormPage'

afterEach(() => vi.unstubAllGlobals())

test('shows the server field error and keeps the form', async () => {
  const problem = { detail: 'Invalid', errors: { endDate: 'La data di fine precede quella di inizio' } }
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(problem), { status: 400 }))
  vi.stubGlobal('fetch', fetchMock)
  const user = userEvent.setup()
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/trips/new']}>
          <Routes>
            <Route path="/trips/new" element={<TripFormPage />} />
            <Route path="/" element={<p>dashboard</p>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
  await user.type(screen.getByLabelText(new RegExp(`^${m.tripForm.name}`)), 'Roma')
  await user.type(screen.getByLabelText(new RegExp(m.tripForm.startDate)), '2026-05-10')
  await user.type(screen.getByLabelText(new RegExp(m.tripForm.endDate)), '2026-05-01')
  await user.click(screen.getByRole('button', { name: m.tripForm.save }))
  expect(await screen.findByText(problem.errors.endDate)).toBeInTheDocument()
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ name: 'Roma', destinations: [], startDate: '2026-05-10', endDate: '2026-05-01' })
  expect(screen.queryByText('dashboard')).not.toBeInTheDocument()
})

test('a failed background refetch keeps the form and the typed edits', async () => {
  let fail!: () => void
  vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise((_, reject) => (fail = () => reject(new TypeError('offline'))))))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  queryClient.setQueryData(['trip', 't1'], {
    id: 't1', name: 'Roma', destinations: ['Roma'], startDate: '2026-05-01', endDate: '2026-05-05', coverUrl: null,
    activities: [], bookings: [], documents: [], checklists: [],
  })
  const user = userEvent.setup()
  render(
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/trips/t1/edit']}>
          <Routes>
            <Route path="/trips/:id/edit" element={<TripFormPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
  const name = screen.getByLabelText(new RegExp(`^${m.tripForm.name}`))
  await user.type(name, ' 2027')
  fail()
  await waitFor(() => expect(queryClient.getQueryState(['trip', 't1'])?.status).toBe('error'))
  expect(screen.getByLabelText(new RegExp(`^${m.tripForm.name}`))).toHaveValue('Roma 2027')
})
