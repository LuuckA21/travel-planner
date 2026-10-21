import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { expect, test, vi } from 'vitest'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import ActivityForm from '../trips/ActivityForm'
import BookingDetail from '../trips/BookingDetail'
import BookingForm from '../trips/BookingForm'
import BookingsTab from '../trips/BookingsTab'
import ChecklistsTab from '../trips/ChecklistsTab'
import DocumentsTab from '../trips/DocumentsTab'
import ItineraryTab from '../trips/ItineraryTab'
import TripFormPage from '../trips/TripFormPage'

vi.mock('./useOnline', () => ({ useOnline: () => false }))

const booking = { id: 'b1', type: 'FLIGHT', title: 'Volo Tokyo', provider: null, reference: null, startAt: '2027-03-10T10:00:00', endAt: null, origin: null, location: null, notes: null } as const
const activity = { id: 'a1', date: '2027-03-10', position: 0, title: 'Tempio', startTime: null, location: null, mapsUrl: null, mapsLink: null, notes: null }
const trip: TripDetail = {
  id: 't1', name: 'Giappone', destinations: ['Tokyo'], startDate: '2027-03-10', endDate: '2027-03-11', coverUrl: '/api/trips/t1/cover?v=1',
  activities: [activity, { ...activity, id: 'a2', title: 'Museo', position: 1 }],
  bookings: [booking],
  documents: [{ id: 'd1', bookingId: 'b1', filename: 'biglietto.pdf', contentType: 'application/pdf', sizeBytes: 10, createdAt: '', fileUrl: '/api/documents/d1/file' }],
  checklists: [{ id: 'c1', name: 'Valigia', position: 0, items: [{ id: 'i1', text: 'Passaporto', done: false, position: 0 }] }],
}

function renderAt(ui: ReactNode, path = '/', route = '*') {
  const qc = new QueryClient()
  qc.setQueryData(['trip', 't1'], trip)
  render(
    <MantineProvider>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path={route} element={ui} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )
}
const allButtonsDisabled = () => {
  const buttons = screen.getAllByRole('button')
  expect(buttons.length).toBeGreaterThan(0)
  for (const b of buttons) expect(b, b.textContent ?? '').toBeDisabled()
}
const button = (name: string) => screen.getByRole('button', { name })

test('mutating controls are disabled offline', () => {
  renderAt(<ChecklistsTab trip={trip} />)
  allButtonsDisabled()
  expect(screen.getByRole('checkbox', { name: 'Passaporto' })).toBeDisabled()
  expect(screen.getByRole('textbox', { name: `${m.checklists.newItem} Valigia` })).toBeDisabled()
  expect(screen.getByRole('textbox', { name: m.checklists.newList })).toBeDisabled()
})

test('itinerary add, edit and reorder are disabled offline', () => {
  renderAt(<ItineraryTab trip={trip} />)
  allButtonsDisabled()
})

test('activity form cannot save or delete offline', () => {
  renderAt(<ActivityForm tripId="t1" activity={activity} date={activity.date} onDone={() => {}} />)
  expect(button(m.itinerary.save)).toBeDisabled()
  expect(button(m.itinerary.delete)).toBeDisabled()
  expect(button(m.itinerary.cancel)).toBeEnabled()
})

test('booking form cannot save or delete offline', () => {
  renderAt(<BookingForm tripId="t1" booking={booking} onDone={() => {}} />)
  expect(button(m.bookings.save)).toBeDisabled()
  expect(button(m.bookings.delete)).toBeDisabled()
  expect(button(m.bookings.cancel)).toBeEnabled()
})

test('bookings tab cannot add offline', () => {
  renderAt(<BookingsTab trip={trip} />)
  allButtonsDisabled()
})

test('booking detail cannot edit or upload offline', () => {
  renderAt(<BookingDetail />, '/trips/t1/bookings/b1', '/trips/:id/bookings/:bookingId')
  allButtonsDisabled()
})

test('documents cannot be uploaded, deleted or fetched offline', () => {
  renderAt(<DocumentsTab trip={trip} />)
  allButtonsDisabled()
})

test('trip form cannot save, delete or remove the cover offline', () => {
  renderAt(<TripFormPage />, '/trips/t1/edit', '/trips/:id/edit')
  expect(button(m.tripForm.save)).toBeDisabled()
  expect(button(m.tripForm.delete)).toBeDisabled()
  expect(button(m.tripForm.removeCover)).toBeDisabled()
})
