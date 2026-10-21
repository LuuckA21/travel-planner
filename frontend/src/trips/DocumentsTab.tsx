import { Alert, Anchor, Button, Card, Group, Stack, Text } from '@mantine/core'
import { useState } from 'react'
import { NetworkError } from '../api/client'
import { useDeleteDocument } from '../api/queries'
import type { DocumentMeta, TripDetail } from '../api/types'
import { m } from '../i18n/it'
import { formatSize } from '../lib/documents'
import DocumentUploadButton from './DocumentUploadButton'

const t = m.documents

function DocumentRow({ tripId, doc, bookingTitle }: { tripId: string; doc: DocumentMeta; bookingTitle?: string }) {
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState<unknown>()
  const del = useDeleteDocument(tripId)

  const remove = async () => {
    setError(undefined)
    try {
      await del.mutateAsync(doc.id)
    } catch (err) {
      setError(err)
      setConfirm(false)
    }
  }

  return (
    <Card withBorder padding="sm">
      <Stack gap="xs">
        {error != null && <Alert color="red" role="alert">{error instanceof NetworkError ? m.common.networkError : m.common.genericError}</Alert>}
        <Stack gap={2}>
          <Anchor href={doc.fileUrl} target="_blank" rel="noopener" fw={500} style={{ overflowWrap: 'anywhere' }}>{doc.filename}</Anchor>
          <Text size="sm" c="dimmed">{[formatSize(doc.sizeBytes), bookingTitle].filter(Boolean).join(' · ')}</Text>
        </Stack>
        <Group>
          <Anchor href={`${doc.fileUrl}?download=true`} size="sm" aria-label={`${t.download} ${doc.filename}`}>{t.download}</Anchor>
          <Button variant="subtle" color="red" size="compact-sm" loading={del.isPending} onClick={confirm ? () => void remove() : () => setConfirm(true)}>
            {confirm ? t.deleteConfirm : t.delete}
          </Button>
        </Group>
      </Stack>
    </Card>
  )
}

export default function DocumentsTab({ trip }: { trip: TripDetail }) {
  const titles = new Map(trip.bookings.map((b) => [b.id, b.title]))
  return (
    <Stack>
      {trip.documents.length === 0 && <Text c="dimmed">{t.empty}</Text>}
      {trip.documents.map((d) => (
        <DocumentRow key={d.id} tripId={trip.id} doc={d} bookingTitle={d.bookingId ? titles.get(d.bookingId) : undefined} />
      ))}
      <Group>
        <DocumentUploadButton tripId={trip.id} />
        {/* Wired in Task 24. */}
        <Button variant="default" disabled>{t.offline}</Button>
      </Group>
    </Stack>
  )
}
