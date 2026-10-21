import { Alert, Button, FileButton, Stack } from '@mantine/core'
import { useRef, useState } from 'react'
import { ApiError, NetworkError } from '../api/client'
import { useUploadDocument } from '../api/queries'
import { m } from '../i18n/it'
import { compressImage } from '../lib/images'
import { useOnline } from '../offline/useOnline'

const t = m.documents

function uploadError(err: unknown): string {
  if (err instanceof NetworkError) return m.common.networkError
  if (err instanceof ApiError && err.status === 415) return t.unsupportedType
  if (err instanceof ApiError && err.status === 413) return t.tooLarge
  return m.common.genericError
}

export default function DocumentUploadButton({ tripId, bookingId }: { tripId: string; bookingId?: string }) {
  const upload = useUploadDocument(tripId)
  const [error, setError] = useState<string>()
  const online = useOnline()
  const resetRef = useRef<() => void>(null)

  const onPick = async (file: File | null) => {
    if (!file) return
    setError(undefined)
    try {
      // The compressed Blob has no name; keep the original so the list shows it.
      const blob = await compressImage(file, 2560)
      await upload.mutateAsync({ file: blob === file ? file : new File([blob], file.name, { type: blob.type }), bookingId })
    } catch (err) {
      setError(uploadError(err))
    } finally {
      // Otherwise picking the same file again fires no change event.
      resetRef.current?.()
    }
  }

  return (
    <Stack gap="xs" align="flex-start">
      {error && <Alert color="red" role="alert">{error}</Alert>}
      <FileButton resetRef={resetRef} onChange={(f) => void onPick(f)} accept="application/pdf,image/jpeg,image/png,image/webp">
        {(props) => <Button variant="light" loading={upload.isPending} disabled={!online} {...props}>{t.upload}</Button>}
      </FileButton>
    </Stack>
  )
}
