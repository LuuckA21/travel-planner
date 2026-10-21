import { Alert, Button, Container, Group, Loader, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { NetworkError } from '../api/client'
import { tripQuery, useTrips } from '../api/queries'
import type { TripSummary } from '../api/types'
import { logout } from '../auth/useMe'
import { todayLocal, tripStatus } from '../lib/dates'
import { m } from '../i18n/it'
import OnlineLinkButton from '../offline/OnlineLinkButton'
import { useOnline } from '../offline/useOnline'
import TripCard from './TripCard'

// Each focus/refetch of the trip list must not re-download every upcoming trip.
const PREFETCH_STALE_MS = 5 * 60 * 1000

function Section({ title, trips, today }: { title: string; trips: TripSummary[]; today?: string }) {
  if (trips.length === 0) return null
  return (
    <Stack component="section" aria-label={title} gap="xs">
      <Title order={2} size="h3">{title}</Title>
      <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }}>
        {trips.map((t) => <TripCard key={t.id} trip={t} today={today} />)}
      </SimpleGrid>
    </Stack>
  )
}

export default function DashboardPage() {
  const trips = useTrips()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [logoutError, setLogoutError] = useState<string>()
  const online = useOnline()
  const today = todayLocal()

  const signOut = () =>
    logout(queryClient, navigate).catch((e) => setLogoutError(e instanceof NetworkError ? m.common.networkError : m.common.genericError))

  const all = trips.data ?? []
  const by = (s: ReturnType<typeof tripStatus>) => all.filter((t) => tripStatus(t, today) === s)
  const inProgress = by('inProgress')
  const upcoming = by('upcoming').sort((a, b) => a.startDate.localeCompare(b.startDate))
  const past = by('past').sort((a, b) => b.endDate.localeCompare(a.endDate))

  // Fetch every in-progress and upcoming trip in full, so it is cached for offline use without being opened.
  useEffect(() => {
    if (!online || !trips.data) return
    for (const t of trips.data) if (tripStatus(t, todayLocal()) !== 'past') void queryClient.prefetchQuery({ ...tripQuery(t.id), staleTime: PREFETCH_STALE_MS })
  }, [online, trips.data, trips.dataUpdatedAt, queryClient])

  return (
    <Container size="lg" my="md">
      <Stack>
        <Group justify="space-between" wrap="nowrap">
          <Title order={1}>{m.dashboard.title}</Title>
          <Group gap="xs" wrap="nowrap">
            <OnlineLinkButton to="/trips/new">{m.dashboard.newTrip}</OnlineLinkButton>
            <Button variant="default" onClick={signOut}>{m.auth.logout}</Button>
          </Group>
        </Group>
        {logoutError && <Alert color="red" role="alert">{logoutError}</Alert>}
        {trips.isPending && <Loader />}
        {trips.isError && <Alert color="red" role="alert">{m.common.networkError}</Alert>}
        {trips.data && all.length === 0 && (
          <Stack align="center" py="xl">
            <Text>{m.dashboard.empty}</Text>
            <OnlineLinkButton to="/trips/new">{m.dashboard.newTrip}</OnlineLinkButton>
          </Stack>
        )}
        <Section title={m.dashboard.inProgress} trips={inProgress} today={today} />
        <Section title={m.dashboard.upcoming} trips={upcoming} />
        <Section title={m.dashboard.past} trips={past} />
      </Stack>
    </Container>
  )
}
