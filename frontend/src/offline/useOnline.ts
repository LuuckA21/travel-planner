import { useSyncExternalStore } from 'react'

// False after a NetworkError until a request reaches the server again, even while the browser reports online.
let reachable = true
const listeners = new Set<() => void>()

export function reportReachable(ok: boolean): void {
  if (ok === reachable) return
  reachable = ok
  listeners.forEach((l) => l())
}

function subscribe(onChange: () => void) {
  listeners.add(onChange)
  window.addEventListener('online', onChange)
  window.addEventListener('offline', onChange)
  return () => {
    listeners.delete(onChange)
    window.removeEventListener('online', onChange)
    window.removeEventListener('offline', onChange)
  }
}

export const useOnline = (): boolean => useSyncExternalStore(subscribe, () => navigator.onLine && reachable)
