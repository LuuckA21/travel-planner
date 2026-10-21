import { useIsRestoring, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { NavigateFunction } from 'react-router'
import { ApiError, apiFetch } from '../api/client'
import type { Me } from '../api/types'
import { claimOfflineData, clearOfflineData } from '../offline/persistence'

export type MeStatus = 'loading' | 'authenticated' | 'anonymous' | 'offline'

// Waiting longer for the server only delays the offline start on a dead connection.
const ME_TIMEOUT_MS = 8000

// Indirection so tests can observe the reload.
export const browser = { reload: () => window.location.reload() }

export function useMe(): { me?: Me; status: MeStatus } {
  const queryClient = useQueryClient()
  // 'always': a truly offline load must fail with NetworkError instead of pausing in 'loading' forever.
  const q = useQuery({
    queryKey: ['me'],
    queryFn: () => apiFetch<Me>('/auth/me', { signal: AbortSignal.timeout(ME_TIMEOUT_MS) }),
    retry: false,
    networkMode: 'always',
    // Re-render on every outcome, even an unchanged user: the claim gate below waits for one.
    notifyOnChangeProps: 'all',
  })
  // Offline data survives an expired session (tickets mid-trip); claimOfflineData wipes it if another user signs in.
  const expired = q.error instanceof ApiError && q.error.status === 401
  const userId = q.data?.id
  // Answered = a /auth/me outcome (success or failure) newer than the cache state once restoring ended. Not
  // isFetchedAfterMount: hydrating a persisted snapshot after mount bumps the same counters before the server answers.
  const state = queryClient.getQueryState(['me'])
  const outcomes = (state?.dataUpdateCount ?? 0) + (state?.errorUpdateCount ?? 0)
  const [baseline, setBaseline] = useState<number>()
  const restoring = useIsRestoring()
  if (!restoring && baseline === undefined) setBaseline(outcomes)
  const answered = baseline !== undefined && outcomes > baseline
  const [claimed, setClaimed] = useState<string>()
  // Nothing renders until the server has answered (or failed: offline) and the user owns the cached data.
  // Another user's data (restored, or left over before this login) is wiped and the page reloaded.
  useEffect(() => {
    if (!userId || !answered || expired) return
    let live = true
    void claimOfflineData(queryClient, userId).then((wiped) => (wiped ? browser.reload() : live && setClaimed(userId)))
    return () => {
      live = false
    }
  }, [userId, answered, expired, queryClient])
  // A 401 wins over cached data; any other failure keeps the last known user.
  if (expired) return { status: 'anonymous' }
  if (q.data && claimed !== q.data.id) return { status: 'loading' }
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
