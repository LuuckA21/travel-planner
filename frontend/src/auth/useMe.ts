import { useQuery, type QueryClient } from '@tanstack/react-query'
import type { NavigateFunction } from 'react-router'
import { ApiError, apiFetch } from '../api/client'
import type { Me } from '../api/types'
import { clearOfflineData } from '../offline/persistence'

export type MeStatus = 'loading' | 'authenticated' | 'anonymous' | 'offline'

export function useMe(): { me?: Me; status: MeStatus } {
  const q = useQuery({ queryKey: ['me'], queryFn: () => apiFetch<Me>('/auth/me'), retry: false })
  // A 401 wins over cached data; any other failure keeps the last known user.
  if (q.error instanceof ApiError && q.error.status === 401) return { status: 'anonymous' }
  if (q.error) return { me: q.data, status: 'offline' }
  return q.data ? { me: q.data, status: 'authenticated' } : { status: 'loading' }
}

export async function logout(queryClient: QueryClient, navigate: NavigateFunction): Promise<void> {
  await apiFetch<void>('/auth/logout', { method: 'POST' })
  await clearOfflineData(queryClient)
  navigate('/login')
}
