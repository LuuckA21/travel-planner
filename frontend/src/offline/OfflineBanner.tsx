import { Box } from '@mantine/core'
import { useQueryClient } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import { m } from '../i18n/it'
import { useOnline } from './useOnline'

export default function OfflineBanner() {
  const online = useOnline()
  const cache = useQueryClient().getQueryCache()
  const updatedAt = useSyncExternalStore(
    (onChange) => cache.subscribe(onChange),
    () => Math.max(0, ...cache.getAll().map((q) => q.state.dataUpdatedAt)),
  )
  if (online) return null
  const time = updatedAt ? new Date(updatedAt).toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' }) : undefined
  return (
    <Box role="status" bg="yellow.1" ta="center" py={4} px="md" fz="sm">
      {m.offline.banner(time)}
    </Box>
  )
}
