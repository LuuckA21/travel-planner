import type { QueryClient } from '@tanstack/react-query'
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister'
import type { PersistedClient, Persister, PersistQueryClientOptions } from '@tanstack/react-query-persist-client'
import { del, get, set } from 'idb-keyval'
import { version } from '../../package.json'

const CACHE_KEY = 'travel-planner-query-cache'
// Owner of everything offline, kept apart from the snapshot so the documents cache is wiped even if the snapshot is gone.
const OWNER_KEY = 'travel-planner-owner'
export const DOCUMENTS_CACHE = 'documents'

// Owner of the data currently in memory: restored with the snapshot, claimed by the signed-in user, reset on clear.
let ownerId: string | undefined

export function createPersister(): Persister {
  const base = createAsyncStoragePersister({ storage: { getItem: get, setItem: set, removeItem: del }, key: CACHE_KEY })
  return {
    // Tagged when the snapshot is taken, so a snapshot and its owner are always written together.
    persistClient: (client) => base.persistClient({ ...client, ownerId } as PersistedClient),
    restoreClient: async () => {
      const client = await base.restoreClient()
      ownerId = (client as { ownerId?: string } | undefined)?.ownerId
      return client
    },
    removeClient: base.removeClient,
  }
}

export const persistOptions: Omit<PersistQueryClientOptions, 'queryClient'> = {
  persister: createPersister(),
  // Longer than the longest trip (365 days), so a trip planned months ahead stays available offline.
  maxAge: 400 * 24 * 60 * 60 * 1000,
  buster: version,
  dehydrateOptions: {
    // Keep data whose last refetch failed (e.g. offline): the default persists only 'success' queries, so the
    // second offline start would lose ['me'] and everything else refetched while offline.
    shouldDehydrateQuery: (query) => query.state.data !== undefined,
    // Editing needs a connection; a paused mutation must not be persisted and replayed later.
    shouldDehydrateMutation: () => false,
  },
}

// Wipes everything cached for the signed-in user: memory, the persisted snapshot and the offline documents.
export async function clearOfflineData(queryClient: QueryClient): Promise<void> {
  // Reset first: clear() triggers snapshots, which must not carry the previous owner.
  ownerId = undefined
  queryClient.clear()
  await Promise.all([del(CACHE_KEY), del(OWNER_KEY), globalThis.caches?.delete(DOCUMENTS_CACHE)])
}

// Called with the user the server confirmed; returns true when another user's data had to be wiped.
export async function claimOfflineData(queryClient: QueryClient, userId: string): Promise<boolean> {
  const owners = [ownerId, await get<string>(OWNER_KEY)]
  const other = owners.some((o) => o !== undefined && o !== userId)
  if (other) await clearOfflineData(queryClient)
  ownerId = userId
  if (owners[1] !== userId) await set(OWNER_KEY, userId)
  return other
}
