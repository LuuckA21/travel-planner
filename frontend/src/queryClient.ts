import { QueryClient } from '@tanstack/react-query'

// Shared by App and logout so both act on the same cache.
export const queryClient = new QueryClient()
