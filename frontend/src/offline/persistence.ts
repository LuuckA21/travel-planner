import type { QueryClient } from '@tanstack/react-query'

// Wipes everything cached for the signed-in user. Task 24 extends this with the persisted store.
export async function clearOfflineData(queryClient: QueryClient): Promise<void> {
  queryClient.clear()
}
