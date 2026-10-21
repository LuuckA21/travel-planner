import { Button, Card, Group, Modal, Stack, Text } from '@mantine/core'
import { useState } from 'react'
import { Link } from 'react-router'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import { bookingIcon } from '../lib/bookings'
import { formatDateTime } from '../lib/dates'
import { useOnline } from '../offline/useOnline'
import BookingForm from './BookingForm'

const t = m.bookings

export default function BookingsTab({ trip }: { trip: TripDetail }) {
  const [adding, setAdding] = useState(false)
  const online = useOnline()
  const bookings = [...trip.bookings].sort((a, b) => a.startAt.localeCompare(b.startAt))

  return (
    <Stack>
      {bookings.length === 0 && <Text c="dimmed">{t.empty}</Text>}
      {bookings.map((b) => (
        <Card key={b.id} withBorder padding="sm" component={Link} to={`/trips/${trip.id}/bookings/${b.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
          <Group wrap="nowrap" align="flex-start">
            <Text aria-label={t.types[b.type]} role="img" size="xl">{bookingIcon[b.type]}</Text>
            <Stack gap={2}>
              <Text fw={500}>{b.title}</Text>
              <Text size="sm" c="dimmed">{formatDateTime(b.startAt)}</Text>
            </Stack>
          </Group>
        </Card>
      ))}
      <Button variant="light" style={{ alignSelf: 'flex-start' }} disabled={!online} onClick={() => setAdding(true)}>{t.add}</Button>
      <Modal opened={adding} onClose={() => setAdding(false)} title={t.newTitle}>
        <BookingForm tripId={trip.id} onDone={() => setAdding(false)} />
      </Modal>
    </Stack>
  )
}
