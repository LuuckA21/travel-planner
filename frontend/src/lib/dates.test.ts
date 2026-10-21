import { afterEach, expect, test, vi } from 'vitest'
import { dayNumber, daysUntil, formatDayHeader, todayLocal, tripStatus } from './dates'

afterEach(() => vi.useRealTimers())

test('todayLocal uses local date, not UTC', () => {
  // 23:30 in Guatemala (UTC-6) is already the 13th in UTC
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2027, 2, 12, 23, 30))
  expect(todayLocal()).toBe('2027-03-12')
})

test('tripStatus boundaries', () => {
  const trip = { startDate: '2027-03-10', endDate: '2027-03-14' }
  expect(tripStatus(trip, '2027-03-09')).toBe('upcoming')
  expect(tripStatus(trip, '2027-03-10')).toBe('inProgress')
  expect(tripStatus(trip, '2027-03-14')).toBe('inProgress')
  expect(tripStatus(trip, '2027-03-15')).toBe('past')
})

test('dayNumber and daysUntil count calendar days across month ends', () => {
  const trip = { startDate: '2027-02-27', endDate: '2027-03-05' }
  expect(dayNumber(trip, '2027-02-27')).toBe(1)
  expect(dayNumber(trip, '2027-03-02')).toBe(4)
  expect(daysUntil('2027-03-01', '2027-02-27')).toBe(2)
  expect(daysUntil('2027-02-27', '2027-03-01')).toBe(-2)
})

test('formatDayHeader', () => {
  expect(formatDayHeader('2027-03-12', 3)).toBe('Ven 12 mar · Giorno 3')
})
