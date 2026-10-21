import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { apiFetch, apiUpload } from './client'
import type { TripDetail, TripSummary } from './types'

export interface TripInput {
  name: string
  destinations: string[]
  startDate: string
  endDate: string
}

export const useTrips = () => useQuery({ queryKey: ['trips'], queryFn: () => apiFetch<TripSummary[]>('/trips') })

export const useTrip = (id: string | undefined) =>
  useQuery({ queryKey: ['trip', id], queryFn: () => apiFetch<TripDetail>(`/trips/${id}`), enabled: !!id })

const invalidateTrip = (qc: QueryClient, id: string) =>
  Promise.all([qc.invalidateQueries({ queryKey: ['trips'] }), qc.invalidateQueries({ queryKey: ['trip', id] })])

export function useSaveTrip() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id?: string; data: TripInput }) =>
      apiFetch<TripDetail>(id ? `/trips/${id}` : '/trips', { method: id ? 'PUT' : 'POST', body: JSON.stringify(data) }),
    onSuccess: (trip) => invalidateTrip(qc, trip.id),
  })
}

export function useDeleteTrip() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/trips/${id}`, { method: 'DELETE' }),
    onSuccess: (_, id) => {
      // Refetching a deleted trip would 404, so drop it instead of invalidating.
      qc.removeQueries({ queryKey: ['trip', id] })
      return qc.invalidateQueries({ queryKey: ['trips'] })
    },
  })
}

export function useUploadCover() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, image }: { id: string; image: Blob }) => {
      const form = new FormData()
      form.append('file', image, 'cover')
      return apiUpload<TripSummary>(`/trips/${id}/cover`, form, 'PUT')
    },
    onSuccess: (_, { id }) => invalidateTrip(qc, id),
  })
}

export function useDeleteCover() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/trips/${id}/cover`, { method: 'DELETE' }),
    onSuccess: (_, id) => invalidateTrip(qc, id),
  })
}
