import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// jsdom lacks matchMedia, which Mantine's color scheme handling needs.
window.matchMedia ??= (() => ({ matches: false, addEventListener() {}, removeEventListener() {} })) as never

// jsdom has no IndexedDB; an in-memory idb-keyval keeps the persistence code paths real.
vi.mock('idb-keyval', () => {
  const store = new Map<IDBValidKey, unknown>()
  return {
    get: async (key: IDBValidKey) => store.get(key),
    set: async (key: IDBValidKey, value: unknown) => void store.set(key, value),
    del: async (key: IDBValidKey) => void store.delete(key),
  }
})

// Vitest globals are off, so RTL's automatic cleanup does not register.
afterEach(cleanup)

// A NetworkError in one test must not leave the next one offline (importActual: some files mock this module).
afterEach(async () => (await vi.importActual<typeof import('../offline/useOnline')>('../offline/useOnline')).reportReachable(true))
