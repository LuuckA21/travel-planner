import { QueryClient } from '@tanstack/react-query'

// Shared by App and logout so both act on the same cache.
// gcTime Infinity: unused queries (e.g. prefetched trips) must stay in memory to be persisted for offline use;
// 30 days in ms would overflow setTimeout and evict them at once.
export const queryClient = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity } } })
