import { Alert, Button, Group, Stack, Textarea, TextInput } from '@mantine/core'
import { useState, type FormEvent } from 'react'
import { ApiError, NetworkError } from '../api/client'
import { useDeleteActivity, useSaveActivity } from '../api/queries'
import type { Activity } from '../api/types'
import { m } from '../i18n/it'
import { useOnline } from '../offline/useOnline'

const t = m.itinerary

interface Props {
  tripId: string
  // Existing activity to edit, or the date to prefill for a new one.
  activity?: Activity
  date: string
  onDone: () => void
}

export default function ActivityForm({ tripId, activity, date, onDone }: Props) {
  const [day, setDay] = useState(activity?.date ?? date)
  const [title, setTitle] = useState(activity?.title ?? '')
  const [time, setTime] = useState(activity?.startTime?.slice(0, 5) ?? '')
  const [location, setLocation] = useState(activity?.location ?? '')
  const [mapsUrl, setMapsUrl] = useState(activity?.mapsUrl ?? '')
  const [notes, setNotes] = useState(activity?.notes ?? '')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<unknown>()
  const online = useOnline()
  const save = useSaveActivity(tripId)
  const del = useDeleteActivity(tripId)

  const fields = error instanceof ApiError ? (error.fieldErrors ?? {}) : {}
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
        id: activity?.id,
        data: {
          date: day,
          title,
          startTime: time || null,
          location: location.trim() || null,
          mapsUrl: mapsUrl.trim() || null,
          notes: notes.trim() || null,
        },
      }),
    )
  }

  return (
    <form onSubmit={submit}>
      <Stack>
        {generic && <Alert color="red" role="alert">{generic}</Alert>}
        <TextInput label={t.date} type="date" required value={day} onChange={(e) => setDay(e.currentTarget.value)} error={fields.date} />
        <TextInput label={t.title} required maxLength={200} value={title} onChange={(e) => setTitle(e.currentTarget.value)} error={fields.title} />
        <TextInput label={t.time} type="time" value={time} onChange={(e) => setTime(e.currentTarget.value)} error={fields.startTime} />
        <TextInput label={t.location} maxLength={300} value={location} onChange={(e) => setLocation(e.currentTarget.value)} error={fields.location} />
        <TextInput label={t.mapsUrl} maxLength={2000} value={mapsUrl} onChange={(e) => setMapsUrl(e.currentTarget.value)} error={fields.mapsUrl} />
        <Textarea label={t.notes} rows={3} maxLength={5000} value={notes} onChange={(e) => setNotes(e.currentTarget.value)} error={fields.notes} />
        <Group justify="space-between">
          {activity ? (
            <Button
              variant="outline"
              color="red"
              loading={del.isPending}
              disabled={!online}
              onClick={confirmDelete ? () => void run(() => del.mutateAsync(activity.id)) : () => setConfirmDelete(true)}
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
