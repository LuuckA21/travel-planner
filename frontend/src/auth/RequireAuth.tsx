import { Center, Loader, Text } from '@mantine/core'
import { Navigate, Outlet } from 'react-router'
import { m } from '../i18n/it'
import { useMe } from './useMe'

export default function RequireAuth() {
  const { me, status } = useMe()
  if (status === 'anonymous') return <Navigate to="/login" replace />
  if (status === 'loading') return <Center h="100vh"><Loader /></Center>
  if (!me) return <Center h="100vh"><Text>{m.common.networkError}</Text></Center>
  return <Outlet />
}
