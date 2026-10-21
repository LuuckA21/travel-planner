import { Alert, Anchor, Button, Container, Group, Loader, Modal, Stack, Text, Title } from '@mantine/core'
import { useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useTrip } from '../api/queries'
import { m } from '../i18n/it'
import { bookingIcon } from '../lib/bookings'
import { formatDateTime } from '../lib/dates'
import BookingForm from './BookingForm'
import DocumentUploadButton from './DocumentUploadButton'

const t = m.bookings

export default function BookingDetail() {
  const { id, bookingId } = useParams()
  const trip = useTrip(id)
  const [editing, setEditing] = useState(false)

  // Check data first: a failed background refetch must not replace content.
  if (!trip.data) return trip.isError ? <Alert m="md" color="red" role="alert">{m.common.networkError}</Alert> : <Loader m="md" />
  const booking = trip.data.bookings.find((b) => b.id === bookingId)
  // Also reached after deleting this booking from the edit modal.
  if (!booking) return <Navigate to={`/trips/${trip.data.id}/bookings`} replace />
  const documents = trip.data.documents.filter((d) => d.bookingId === booking.id)

  const rows: [string, string | null][] = [
    [t.provider, booking.provider],
    [t.reference, booking.reference],
    [t.startAt, formatDateTime(booking.startAt)],
    [t.endAt, booking.endAt && formatDateTime(booking.endAt)],
    [t.origin, booking.origin],
    [t.location, booking.location],
    [t.notes, booking.notes],
  ]

  return (
    <Container size="lg" my="md">
      <Stack>
        <Anchor component={Link} to={`/trips/${trip.data.id}/bookings`}>← {t.back}</Anchor>
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Title order={1}>
            <span role="img" aria-label={t.types[booking.type]}>{bookingIcon[booking.type]}</span> {booking.title}
          </Title>
          <Button variant="default" size="xs" onClick={() => setEditing(true)}>{t.edit}</Button>
        </Group>
        {rows.map(([label, value]) =>
          value ? (
            <Stack key={label} gap={0}>
              <Text size="sm" c="dimmed">{label}</Text>
              <Text style={{ whiteSpace: 'pre-wrap' }}>{value}</Text>
            </Stack>
          ) : null,
        )}
        <Stack gap="xs">
          <Title order={2} size="h4">{t.documents}</Title>
          {documents.length === 0 && <Text c="dimmed">{t.noDocuments}</Text>}
          {documents.map((d) => (
            <Anchor key={d.id} href={d.fileUrl} target="_blank" rel="noopener">{d.filename}</Anchor>
          ))}
          <DocumentUploadButton tripId={trip.data.id} bookingId={booking.id} />
        </Stack>
      </Stack>
      <Modal opened={editing} onClose={() => setEditing(false)} title={t.editTitle}>
        <BookingForm tripId={trip.data.id} booking={booking} onDone={() => setEditing(false)} />
      </Modal>
    </Container>
  )
}
