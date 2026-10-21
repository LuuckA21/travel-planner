import type { Activity, Booking, TripDetail } from '../api/types'
import { dayNumber, todayLocal } from './dates'

export interface DaySection {
  date: string
  dayNumber: number
  activities: Activity[]
  bookings: Booking[]
}

const byPosition = (a: Activity, b: Activity) => a.position - b.position
const dateOf = (dateTime: string) => dateTime.slice(0, 10)

export function groupByDay(trip: TripDetail): { days: DaySection[]; outside: Activity[] } {
  const days: DaySection[] = []
  const byDate = new Map<string, DaySection>()
  for (let d = trip.startDate; d <= trip.endDate; d = nextDate(d)) {
    const section = { date: d, dayNumber: dayNumber(trip, d), activities: [], bookings: [] }
    days.push(section)
    byDate.set(d, section)
  }

  const outside: Activity[] = []
  for (const a of [...trip.activities].sort(byPosition)) {
    const section = byDate.get(a.date)
    if (section) section.activities.push(a)
    else outside.push(a)
  }
  for (const b of [...trip.bookings].sort((a, c) => a.startAt.localeCompare(c.startAt))) {
    const start = dateOf(b.startAt)
    const end = b.endAt ? dateOf(b.endAt) : start
    byDate.get(start)?.bookings.push(b)
    if (end !== start) byDate.get(end)?.bookings.push(b)
  }
  return { days, outside }
}

export function nextItems(trip: TripDetail, now: Date): { activities: Activity[]; booking?: Booking } {
  const today = todayLocal(now)
  const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  const nowIso = `${today}T${time}:00`
  const activities = trip.activities
    .filter((a) => a.date > today || (a.date === today && (!a.startTime || a.startTime.slice(0, 5) >= time)))
    .sort((a, b) => a.date.localeCompare(b.date) || byPosition(a, b))
    .slice(0, 3)
  const booking = trip.bookings
    .filter((b) => b.startAt >= nowIso)
    .sort((a, b) => a.startAt.localeCompare(b.startAt))[0]
  return { activities, booking }
}

function nextDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return todayLocal(new Date(y, m - 1, d + 1))
}
