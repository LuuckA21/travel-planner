import { afterEach, expect, test, vi } from 'vitest'
import type { Activity, Booking, TripDetail } from '../api/types'
import { groupByDay, nextItems } from './itinerary'

afterEach(() => vi.useRealTimers())

const act = (id: string, date: string, position: number, startTime: string | null = null): Activity => ({
  id, date, position, title: id, startTime, location: null, mapsUrl: null, mapsLink: null, notes: null,
})
const booking = (id: string, startAt: string, endAt: string | null): Booking => ({
  id, type: 'FLIGHT', title: id, provider: null, reference: null, startAt, endAt, origin: null, location: null, notes: null,
})
const trip = (activities: Activity[] = [], bookings: Booking[] = []) =>
  ({ startDate: '2027-03-10', endDate: '2027-03-14', activities, bookings }) as unknown as TripDetail

test('one section per trip date, activities ordered by position', () => {
  const { days } = groupByDay(trip([act('b', '2027-03-11', 2), act('a', '2027-03-11', 1)]))
  expect(days.map((d) => d.date)).toEqual(['2027-03-10', '2027-03-11', '2027-03-12', '2027-03-13', '2027-03-14'])
  expect(days[1].dayNumber).toBe(2)
  expect(days[1].activities.map((a) => a.id)).toEqual(['a', 'b'])
})

test('booking spanning days appears on start and end day only', () => {
  const { days } = groupByDay(trip([], [booking('hotel', '2027-03-10T15:00:00', '2027-03-14T10:00:00')]))
  expect(days.filter((d) => d.bookings.length).map((d) => d.date)).toEqual(['2027-03-10', '2027-03-14'])
})

test('bookings within a day are ordered by start time', () => {
  const { days } = groupByDay(
    trip([], [booking('late', '2027-03-11T18:00:00', null), booking('early', '2027-03-11T08:00:00', null), booking('night', '2027-03-10T22:00:00', '2027-03-11T06:00:00')]),
  )
  expect(days[1].bookings.map((b) => b.id)).toEqual(['night', 'early', 'late'])
})

test('overnight flight appears on both days', () => {
  const { days } = groupByDay(trip([], [booking('f', '2027-03-11T22:30:00', '2027-03-12T06:15:00')]))
  expect(days.filter((d) => d.bookings.length).map((d) => d.date)).toEqual(['2027-03-11', '2027-03-12'])
})

test('same-day booking is not duplicated; out-of-range booking is omitted', () => {
  const { days } = groupByDay(
    trip([], [booking('a', '2027-03-11T10:00:00', '2027-03-11T12:00:00'), booking('b', '2027-03-20T10:00:00', null)]),
  )
  expect(days.flatMap((d) => d.bookings.map((b) => b.id))).toEqual(['a'])
})

test('activities outside trip dates go to outside', () => {
  const { days, outside } = groupByDay(trip([act('in', '2027-03-10', 1), act('out', '2027-03-20', 1)]))
  expect(outside.map((a) => a.id)).toEqual(['out'])
  expect(days[0].activities.map((a) => a.id)).toEqual(['in'])
})

test('nextItems skips past timed activities today', () => {
  const t = trip(
    [
      act('past', '2027-03-12', 1, '09:00:00'),
      act('untimed', '2027-03-12', 2),
      act('later', '2027-03-12', 3, '18:00:00'),
      act('tomorrow', '2027-03-13', 1, '08:00:00'),
      act('yesterday', '2027-03-11', 1),
      act('extra', '2027-03-14', 1),
    ],
    [booking('old', '2027-03-12T08:00:00', null), booking('next', '2027-03-12T20:00:00', null)],
  )
  const r = nextItems(t, new Date(2027, 2, 12, 14, 0))
  expect(r.activities.map((a) => a.id)).toEqual(['untimed', 'later', 'tomorrow'])
  expect(r.booking?.id).toBe('next')
})
