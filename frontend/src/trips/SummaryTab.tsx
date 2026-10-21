import { Card, Stack, Text, Title } from '@mantine/core'
import { useState } from 'react'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import { dayNumber, formatDayHeader } from '../lib/dates'
import { nextItems } from '../lib/itinerary'

const t = m.trip

export default function SummaryTab({ trip }: { trip: TripDetail }) {
  // Fixed at mount; the tab remounts whenever the user navigates back to it.
  const [now] = useState(() => new Date())
  const { activities, booking } = nextItems(trip, now)
  const bookingDate = booking?.startAt.slice(0, 10)
  return (
    <Stack>
      <Stack component="section" aria-label={t.nextActivities} gap="xs">
        <Title order={2} size="h4">{t.nextActivities}</Title>
        {activities.length === 0 && <Text c="dimmed">{t.nothingNext}</Text>}
        {activities.map((a) => (
          <Card key={a.id} withBorder padding="sm">
            <Text fw={500}>{a.title}</Text>
            <Text size="sm" c="dimmed">
              {formatDayHeader(a.date, dayNumber(trip, a.date))}
              {a.startTime && ` · ${a.startTime.slice(0, 5)}`}
            </Text>
          </Card>
        ))}
      </Stack>
      <Stack component="section" aria-label={t.nextBooking} gap="xs">
        <Title order={2} size="h4">{t.nextBooking}</Title>
        {booking && bookingDate ? (
          <Card withBorder padding="sm">
            <Text fw={500}>{booking.title}</Text>
            <Text size="sm" c="dimmed">
              {formatDayHeader(bookingDate, dayNumber(trip, bookingDate))} · {booking.startAt.slice(11, 16)}
            </Text>
          </Card>
        ) : (
          <Text c="dimmed">{t.nothingNext}</Text>
        )}
      </Stack>
    </Stack>
  )
}
