import type { TripDetail } from '../api/types'
import { DOCUMENTS_CACHE } from './persistence'

// Stores straight into the cache the service worker serves cache-first, so "ok" means the file is really offline,
// even before the service worker controls the page.
export async function makeTripOffline(trip: TripDetail): Promise<{ ok: number; failed: number }> {
  const urls = [...trip.documents.map((d) => d.fileUrl), ...(trip.coverUrl ? [trip.coverUrl] : [])]
  const cache = await caches.open(DOCUMENTS_CACHE)
  const results = await Promise.allSettled(urls.map((url) => cache.add(url)))
  const ok = results.filter((r) => r.status === 'fulfilled').length
  return { ok, failed: results.length - ok }
}
