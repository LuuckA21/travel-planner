import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import type { NavigateFunction } from 'react-router'
import { ApiError, apiFetch } from '../api/client'
import type { Me } from '../api/types'
import { clearOfflineData } from '../offline/persistence'

export type MeStatus = 'loading' | 'authenticated' | 'anonymous' | 'offline'

export function useMe(): { me?: Me; status: MeStatus } {
  const queryClient = useQueryClient()
  // 'always': a truly offline load must fail with NetworkError instead of pausing in 'loading' forever.
  const q = useQuery({ queryKey: ['me'], queryFn: () => apiFetch<Me>('/auth/me'), retry: false, networkMode: 'always' })
  const expired = q.error instanceof ApiError && q.error.status === 401
  // The session is gone: drop the previous user's cached data so the next user on this device never sees it.
  useEffect(() => {
    if (expired) void clearOfflineData(queryClient)
  }, [expired, queryClient])
  // A 401 wins over cached data; any other failure keeps the last known user.
  if (expired) return { status: 'anonymous' }
  if (q.error) return { me: q.data, status: 'offline' }
  return q.data ? { me: q.data, status: 'authenticated' } : { status: 'loading' }
}

export async function logout(queryClient: QueryClient, navigate: NavigateFunction): Promise<void> {
  try {
    await apiFetch<void>('/auth/logout', { method: 'POST' })
  } catch (e) {
    // 401: the session already expired, so the local sign-out still proceeds. Anything else keeps the session.
    if (!(e instanceof ApiError && e.status === 401)) throw e
  }
  await clearOfflineData(queryClient)
  navigate('/login')
}
