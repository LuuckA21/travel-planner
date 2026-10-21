import { Box, Button, Group, Text } from '@mantine/core'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { m } from '../i18n/it'

export default function UpdatePrompt() {
  const { needRefresh: [needRefresh], updateServiceWorker } = useRegisterSW()
  if (!needRefresh) return null
  return (
    <Box role="status" bg="blue.0" py={4} px="md">
      <Group justify="center" gap="xs">
        <Text size="sm">{m.pwa.updateAvailable}</Text>
        <Button size="compact-sm" onClick={() => void updateServiceWorker(true)}>{m.pwa.update}</Button>
      </Group>
    </Box>
  )
}
