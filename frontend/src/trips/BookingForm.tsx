import { Alert, Button, Group, NativeSelect, Stack, Textarea, TextInput } from '@mantine/core'
import { useState, type FormEvent } from 'react'
import { NetworkError } from '../api/client'
import { useDeleteBooking, useSaveBooking } from '../api/queries'
import type { Booking, BookingType } from '../api/types'
import { m } from '../i18n/it'
import { fieldErrors } from '../lib/fieldErrors'
import { useOnline } from '../offline/useOnline'

const t = m.bookings

interface Props {
  tripId: string
  booking?: Booking
  onDone: () => void
}

const typeOptions = (Object.keys(t.types) as BookingType[]).map((value) => ({ value, label: t.types[value] }))

export default function BookingForm({ tripId, booking, onDone }: Props) {
  const [type, setType] = useState<BookingType>(booking?.type ?? 'FLIGHT')
  const [title, setTitle] = useState(booking?.title ?? '')
  const [provider, setProvider] = useState(booking?.provider ?? '')
  const [reference, setReference] = useState(booking?.reference ?? '')
  // The API returns seconds; datetime-local values are minute precision.
  const [startAt, setStartAt] = useState(booking?.startAt.slice(0, 16) ?? '')
  const [endAt, setEndAt] = useState(booking?.endAt?.slice(0, 16) ?? '')
  const [origin, setOrigin] = useState(booking?.origin ?? '')
  const [location, setLocation] = useState(booking?.location ?? '')
  const [notes, setNotes] = useState(booking?.notes ?? '')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<unknown>()
  const online = useOnline()
  const save = useSaveBooking(tripId)
  const del = useDeleteBooking(tripId)

  const fields = fieldErrors(error, { endAt: m.common.endBeforeStart })
  const generic =
    error && Object.keys(fields).length === 0
      ? error instanceof NetworkError ? m.common.networkError : m.common.genericError
      : undefined

  const run = async (op: () => Promise<unknown>) => {
    setError(undefined)
    try {
      await op()
      onDone()
    } catch (err) {
      setError(err)
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    void run(() =>
      save.mutateAsync({
        id: booking?.id,
        data: {
          type,
          title,
          provider: provider.trim() || null,
          reference: reference.trim() || null,
          startAt,
          endAt: endAt || null,
          origin: origin.trim() || null,
          location: location.trim() || null,
          notes: notes.trim() || null,
        },
      }),
    )
  }

  return (
    <form onSubmit={submit}>
      <Stack>
        {generic && <Alert color="red" role="alert">{generic}</Alert>}
        <NativeSelect label={t.type} data={typeOptions} value={type} onChange={(e) => setType(e.currentTarget.value as BookingType)} error={fields.type} />
        <TextInput label={t.title} required maxLength={200} value={title} onChange={(e) => setTitle(e.currentTarget.value)} error={fields.title} />
        <TextInput label={t.provider} maxLength={200} value={provider} onChange={(e) => setProvider(e.currentTarget.value)} error={fields.provider} />
        <TextInput label={t.reference} maxLength={200} value={reference} onChange={(e) => setReference(e.currentTarget.value)} error={fields.reference} />
        <TextInput label={t.startAt} type="datetime-local" required value={startAt} onChange={(e) => setStartAt(e.currentTarget.value)} error={fields.startAt} />
        <TextInput label={t.endAt} type="datetime-local" value={endAt} onChange={(e) => setEndAt(e.currentTarget.value)} error={fields.endAt} />
        <TextInput label={t.origin} maxLength={200} value={origin} onChange={(e) => setOrigin(e.currentTarget.value)} error={fields.origin} />
        <TextInput label={t.location} maxLength={300} value={location} onChange={(e) => setLocation(e.currentTarget.value)} error={fields.location} />
        <Textarea label={t.notes} rows={3} maxLength={5000} value={notes} onChange={(e) => setNotes(e.currentTarget.value)} error={fields.notes} />
        <Group justify="space-between">
          {booking ? (
            <Button
              variant="outline"
              color="red"
              loading={del.isPending}
              disabled={!online}
              onClick={confirmDelete ? () => void run(() => del.mutateAsync(booking.id)) : () => setConfirmDelete(true)}
            >
              {confirmDelete ? t.deleteConfirm : t.delete}
            </Button>
          ) : (
            <span />
          )}
          <Group>
            <Button variant="default" onClick={onDone}>{t.cancel}</Button>
            <Button type="submit" loading={save.isPending} disabled={!online}>{t.save}</Button>
          </Group>
        </Group>
      </Stack>
    </form>
  )
}
