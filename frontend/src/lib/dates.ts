import { m } from '../i18n/it'

const pad = (n: number) => String(n).padStart(2, '0')

// Parse YYYY-MM-DD as a local date; new Date('YYYY-MM-DD') would be UTC.
const parse = (iso: string) => {
  const [y, mo, d] = iso.split('-').map(Number)
  return new Date(y, mo - 1, d)
}

// Whole calendar days between two dates, immune to DST (rounds the 23/25h days).
const diffDays = (from: string, to: string) =>
  Math.round((parse(to).getTime() - parse(from).getTime()) / 86_400_000)

export function todayLocal(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

export function tripStatus(
  trip: { startDate: string; endDate: string },
  today: string,
): 'upcoming' | 'inProgress' | 'past' {
  if (today < trip.startDate) return 'upcoming'
  return today > trip.endDate ? 'past' : 'inProgress'
}

export const dayNumber = (trip: { startDate: string }, date: string) => diffDays(trip.startDate, date) + 1

export const daysUntil = (date: string, today: string) => diffDays(today, date)

const fmt = new Intl.DateTimeFormat('it-IT', { weekday: 'short', day: 'numeric', month: 'short' })

export function formatDayHeader(date: string, day?: number): string {
  // "ven 12 mar" -> "Ven 12 mar"; strip any trailing dots some ICU versions add
  const text = fmt.format(parse(date)).replace(/\./g, '')
  const label = `${text.charAt(0).toUpperCase()}${text.slice(1)}`
  return day === undefined ? label : `${label} · ${m.itinerary.day} ${day}`
}

const rangeFmt = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'short', year: 'numeric' })

export const formatDateRange = (start: string, end: string) =>
  `${rangeFmt.format(parse(start))} – ${rangeFmt.format(parse(end))}`
