import { QueryClient } from '@tanstack/react-query'
import { persistQueryClientRestore, persistQueryClientSave } from '@tanstack/react-query-persist-client'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'

// Survives vi.resetModules, so a fresh module instance can restore what an earlier "session" persisted.
const store = vi.hoisted(() => new Map<string, unknown>())
vi.mock('idb-keyval', () => ({
  get: async (key: string) => store.get(key),
  set: async (key: string, value: unknown) => void store.set(key, value),
  del: async (key: string) => void store.delete(key),
}))

const cachesMock = { delete: vi.fn(async () => true) }
const snapshot = { timestamp: Date.now(), buster: '', clientState: { queries: [], mutations: [] } }
const seeded = () => {
  const qc = new QueryClient()
  qc.setQueryData(['trips'], [{ id: 't1' }])
  return qc
}
// A new module instance stands in for the next app start: nothing in memory, only what was persisted.
const freshSession = async () => {
  vi.resetModules()
  return import('./persistence')
}

beforeEach(() => vi.stubGlobal('caches', cachesMock))
afterEach(() => {
  vi.unstubAllGlobals()
  cachesMock.delete.mockClear()
  store.clear()
})

test('clearOfflineData clears query cache, persisted state and documents cache', async () => {
  const { clearOfflineData, createPersister } = await freshSession()
  const persister = createPersister()
  await persister.persistClient(snapshot)
  expect(store.size).toBe(1)
  const qc = seeded()

  await clearOfflineData(qc)

  expect(qc.getQueryCache().getAll()).toHaveLength(0)
  expect(store.size).toBe(0)
  expect(await persister.restoreClient()).toBeUndefined()
  expect(cachesMock.delete).toHaveBeenCalledWith('documents')
})

test('the persisted state is tagged with the user it belongs to', async () => {
  const { claimOfflineData, createPersister } = await freshSession()
  await claimOfflineData(seeded(), 'user-a')
  const persister = createPersister()
  await persister.persistClient(snapshot)
  expect(await persister.restoreClient()).toMatchObject({ ownerId: 'user-a' })
})

test('different user triggers clear', async () => {
  const first = await freshSession()
  await first.claimOfflineData(seeded(), 'user-a')
  await first.createPersister().persistClient(snapshot)

  const next = await freshSession()
  await next.createPersister().restoreClient()
  const qc = seeded()
  expect(await next.claimOfflineData(qc, 'user-b')).toBe(true)

  expect(qc.getQueryCache().getAll()).toHaveLength(0)
  expect([...store.values()]).toEqual(['user-b']) // A's snapshot is gone; only the new owner remains
  expect(cachesMock.delete).toHaveBeenCalledWith('documents')
})

test('a different user wipes the offline documents even without a query snapshot', async () => {
  const first = await freshSession()
  await first.claimOfflineData(seeded(), 'user-a')
  expect([...store.values()]).toEqual(['user-a']) // the owner key alone, no snapshot

  const next = await freshSession()
  const qc = seeded()
  expect(await next.claimOfflineData(qc, 'user-b')).toBe(true)
  expect(cachesMock.delete).toHaveBeenCalledWith('documents')
  expect([...store.values()]).toEqual(['user-b'])
})

test('a snapshot written while clearing is not tagged with the previous owner', async () => {
  const { claimOfflineData, clearOfflineData, createPersister } = await freshSession()
  const qc = seeded()
  await claimOfflineData(qc, 'user-a')
  const persister = createPersister()
  // Like PersistQueryClientProvider: every cache event takes a snapshot.
  qc.getQueryCache().subscribe(() => void persister.persistClient(snapshot))
  await clearOfflineData(qc)
  await vi.waitFor(async () => expect(await persister.restoreClient()).toBeDefined())
  expect(await persister.restoreClient()).not.toHaveProperty('ownerId', 'user-a')
})

test('the same user keeps the restored data', async () => {
  const first = await freshSession()
  await first.claimOfflineData(seeded(), 'user-a')
  await first.createPersister().persistClient(snapshot)

  const next = await freshSession()
  await next.createPersister().restoreClient()
  const qc = seeded()
  expect(await next.claimOfflineData(qc, 'user-a')).toBe(false)

  expect(qc.getQueryData(['trips'])).toEqual([{ id: 't1' }])
  expect(store.size).toBe(2) // snapshot and owner
  expect(cachesMock.delete).not.toHaveBeenCalled()
})

test('data whose last refetch failed is still persisted, so a second offline start keeps it', async () => {
  const { persistOptions } = await freshSession()
  const qc = new QueryClient()
  qc.setQueryData(['me'], { id: 'user-a' })
  await qc.fetchQuery({ queryKey: ['me'], queryFn: () => Promise.reject(new Error('offline')), retry: false }).catch(() => {})
  expect(qc.getQueryState(['me'])?.status).toBe('error')

  await persistQueryClientSave({ queryClient: qc, ...persistOptions })
  const restored = new QueryClient()
  await persistQueryClientRestore({ queryClient: restored, ...persistOptions })

  expect(restored.getQueryData(['me'])).toEqual({ id: 'user-a' })
})

test('the persisted cache lives 400 days, longer than the longest trip', async () => {
  const { persistOptions } = await freshSession()
  expect(persistOptions.maxAge).toBe(400 * 24 * 60 * 60 * 1000)
})
