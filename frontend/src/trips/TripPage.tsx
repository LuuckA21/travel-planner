import { Alert, Box, Button, Container, Group, Image, Loader, Stack, Text, Title } from '@mantine/core'
import type { ComponentType } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useTrip } from '../api/queries'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import { daysUntil, dayNumber, formatDateRange, todayLocal, tripStatus } from '../lib/dates'
import BookingsTab from './BookingsTab'
import ItineraryTab from './ItineraryTab'
import SummaryTab from './SummaryTab'

// Tasks 22-23 replace these placeholders.
const Soon = () => <Text c="dimmed">{m.trip.comingSoon}</Text>

const tabs = {
  summary: SummaryTab,
  itinerary: ItineraryTab,
  bookings: BookingsTab,
  documents: Soon,
  checklists: Soon,
} satisfies Record<string, ComponentType<{ trip: TripDetail }>>

type Tab = keyof typeof tabs
const isTab = (v: string): v is Tab => Object.hasOwn(tabs, v)

function progress(trip: TripDetail) {
  const today = todayLocal()
  const status = tripStatus(trip, today)
  if (status === 'upcoming') return m.trip.inDays(daysUntil(trip.startDate, today))
  if (status === 'inProgress') return m.trip.dayOf(dayNumber(trip, today), dayNumber(trip, trip.endDate))
  return undefined
}

export default function TripPage() {
  const { id, tab = 'summary' } = useParams()
  const trip = useTrip(id)

  // Check data first: a failed background refetch must not replace content.
  if (!trip.data) return trip.isError ? <Alert m="md" color="red" role="alert">{m.common.networkError}</Alert> : <Loader m="md" />
  if (!isTab(tab)) return <Navigate to={`/trips/${trip.data.id}/summary`} replace />

  const data = trip.data
  const Tab = tabs[tab]
  return (
    <>
      <Container size="lg" my="md" pb="calc(80px + env(safe-area-inset-bottom))">
        <Stack>
          {data.coverUrl && <Image src={data.coverUrl} alt="" h={160} radius="md" />}
          <Group justify="space-between" align="flex-start" wrap="nowrap">
            <Stack gap={0}>
              <Title order={1}>{data.name}</Title>
              {data.destinations.length > 0 && <Text>{data.destinations.join(' · ')}</Text>}
              <Text size="sm" c="dimmed">{formatDateRange(data.startDate, data.endDate)}</Text>
              <Text size="sm" c="dimmed">{progress(data)}</Text>
            </Stack>
            <Button component={Link} to={`/trips/${data.id}/edit`} size="xs" variant="default">{m.dashboard.edit}</Button>
          </Group>
          <Tab trip={data} />
        </Stack>
      </Container>
      <Box component="nav" aria-label={m.trip.nav} pos="fixed" bottom={0} left={0} right={0} bg="var(--mantine-color-body)" style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
        <Group justify="space-around" pt="xs" pb="calc(var(--mantine-spacing-xs) + env(safe-area-inset-bottom))" wrap="nowrap">
          {(Object.keys(tabs) as Tab[]).map((k) => (
            <Link key={k} to={`/trips/${data.id}/${k}`} aria-current={k === tab ? 'page' : undefined} style={{ fontWeight: k === tab ? 700 : 400, fontSize: 14 }}>
              {m.trip.tabs[k]}
            </Link>
          ))}
        </Group>
      </Box>
    </>
  )
}
