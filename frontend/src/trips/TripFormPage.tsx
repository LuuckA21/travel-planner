import { ActionIcon, Alert, Button, Container, FileInput, Group, Loader, Modal, Stack, Text, TextInput, Title } from '@mantine/core'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, NetworkError } from '../api/client'
import { useDeleteCover, useDeleteTrip, useSaveTrip, useTrip, useUploadCover } from '../api/queries'
import type { TripDetail } from '../api/types'
import { compressImage } from '../lib/images'
import { m } from '../i18n/it'

const t = m.tripForm

function TripForm({ trip }: { trip?: TripDetail }) {
  const navigate = useNavigate()
  const [name, setName] = useState(trip?.name ?? '')
  const [destinations, setDestinations] = useState(trip?.destinations.length ? trip.destinations : [''])
  const [startDate, setStartDate] = useState(trip?.startDate ?? '')
  const [endDate, setEndDate] = useState(trip?.endDate ?? '')
  const [cover, setCover] = useState<File | null>(null)
  const [removeCover, setRemoveCover] = useState(false)
  const [savedId, setSavedId] = useState<string>()
  const [error, setError] = useState<unknown>()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const save = useSaveTrip()
  const upload = useUploadCover()
  const deleteCover = useDeleteCover()
  const del = useDeleteTrip()

  const fields = error instanceof ApiError ? (error.fieldErrors ?? {}) : {}
  const destinationError = Object.entries(fields).find(([k]) => k.startsWith('destinations'))?.[1]
  const generic =
    error && Object.keys(fields).length === 0
      ? error instanceof NetworkError ? m.common.networkError : m.common.genericError
      : undefined
  const busy = save.isPending || upload.isPending || deleteCover.isPending

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError(undefined)
    try {
      // After a partial failure (trip saved, cover failed) a retry must update, not create a duplicate.
      const id = savedId ?? trip?.id
      const data = { name, destinations: destinations.map((d) => d.trim()).filter(Boolean), startDate, endDate }
      const saved = await save.mutateAsync({ id, data })
      setSavedId(saved.id)
      if (cover) await upload.mutateAsync({ id: saved.id, image: await compressImage(cover, 1600) })
      else if (removeCover && trip?.coverUrl) await deleteCover.mutateAsync(saved.id)
      navigate('/')
    } catch (err) {
      setError(err)
    }
  }

  const remove = async () => {
    try {
      await del.mutateAsync(trip!.id)
      navigate('/')
    } catch (err) {
      setConfirmDelete(false)
      setError(err)
    }
  }

  const setDestination = (i: number, value: string) => setDestinations(destinations.map((d, j) => (j === i ? value : d)))

  return (
    <Container size="sm" my="md">
      <form onSubmit={submit}>
        <Stack>
          <Title order={1}>{trip ? t.editTitle : t.newTitle}</Title>
          {generic && <Alert color="red" role="alert">{generic}</Alert>}
          <TextInput label={t.name} required maxLength={120} value={name} onChange={(e) => setName(e.currentTarget.value)} error={fields.name} />
          <Stack gap="xs">
            <Text size="sm" fw={500}>{t.destinations}</Text>
            {destinations.map((d, i) => (
              <Group key={i} wrap="nowrap" align="flex-start">
                <TextInput
                  aria-label={`${t.destination} ${i + 1}`}
                  maxLength={80}
                  value={d}
                  onChange={(e) => setDestination(i, e.currentTarget.value)}
                  style={{ flex: 1 }}
                />
                <ActionIcon
                  variant="default"
                  size={36}
                  aria-label={`${t.removeDestination} ${i + 1}`}
                  onClick={() => setDestinations(destinations.filter((_, j) => j !== i))}
                >
                  ×
                </ActionIcon>
              </Group>
            ))}
            {destinationError && <Text size="sm" c="red" role="alert">{destinationError}</Text>}
            <Button variant="subtle" size="xs" style={{ alignSelf: 'flex-start' }} onClick={() => setDestinations([...destinations, ''])}>
              {t.addDestination}
            </Button>
          </Stack>
          <Group grow align="flex-start">
            <TextInput label={t.startDate} type="date" required value={startDate} onChange={(e) => setStartDate(e.currentTarget.value)} error={fields.startDate} />
            <TextInput label={t.endDate} type="date" required value={endDate} onChange={(e) => setEndDate(e.currentTarget.value)} error={fields.endDate} />
          </Group>
          <FileInput label={t.cover} accept="image/jpeg,image/png,image/webp" clearable value={cover} onChange={setCover} />
          {trip?.coverUrl && !cover && !removeCover && (
            <Button variant="subtle" color="red" size="xs" style={{ alignSelf: 'flex-start' }} onClick={() => setRemoveCover(true)}>
              {t.removeCover}
            </Button>
          )}
          <Group justify="space-between">
            {trip ? <Button variant="outline" color="red" onClick={() => setConfirmDelete(true)}>{t.delete}</Button> : <span />}
            <Group>
              <Button component={Link} to="/" variant="default">{t.cancel}</Button>
              <Button type="submit" loading={busy}>{t.save}</Button>
            </Group>
          </Group>
        </Stack>
      </form>
      <Modal opened={confirmDelete} onClose={() => setConfirmDelete(false)} title={t.deleteTitle}>
        <Stack>
          <Text>{t.deleteBody}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirmDelete(false)}>{t.cancel}</Button>
            <Button color="red" loading={del.isPending} onClick={remove}>{t.deleteConfirm}</Button>
          </Group>
        </Stack>
      </Modal>
    </Container>
  )
}

export default function TripFormPage() {
  const { id } = useParams()
  const trip = useTrip(id)
  if (!id) return <TripForm />
  if (trip.isError) return <Alert m="md" color="red" role="alert">{m.common.networkError}</Alert>
  if (!trip.data) return <Loader m="md" />
  return <TripForm trip={trip.data} />
}
