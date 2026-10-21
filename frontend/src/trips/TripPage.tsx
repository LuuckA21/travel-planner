import { Alert, Anchor, Box, Container, Group, Image, Loader, Stack, Text, Title } from '@mantine/core'
import type { ComponentType } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useTrip } from '../api/queries'
import type { TripDetail } from '../api/types'
import { m } from '../i18n/it'
import OnlineLinkButton from '../offline/OnlineLinkButton'
import { daysUntil, dayNumber, formatDateRange, todayLocal, tripStatus } from '../lib/dates'
import BookingsTab from './BookingsTab'
import ChecklistsTab from './ChecklistsTab'
import DocumentsTab from './DocumentsTab'
import ItineraryTab from './ItineraryTab'
import SummaryTab from './SummaryTab'

const tabs = {
  summary: SummaryTab,
  itinerary: ItineraryTab,
  bookings: BookingsTab,
  documents: DocumentsTab,
  checklists: ChecklistsTab,
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
  if (!trip.data) {
    if (trip.isError) return <Alert m="md" color="red" role="alert">{m.common.networkError}</Alert>
    // Offline and never cached: the query stays paused, so a loader would spin forever.
    return trip.fetchStatus === 'paused' ? <Alert m="md">{m.common.unavailableOffline}</Alert> : <Loader m="md" />
  }
  if (!isTab(tab)) return <Navigate to={`/trips/${trip.data.id}/summary`} replace />

  const data = trip.data
  const Tab = tabs[tab]
  return (
    <>
      <Container size="lg" my="md" pb="calc(80px + env(safe-area-inset-bottom))">
        <Stack>
          <Anchor component={Link} to="/">← {m.trip.back}</Anchor>
          {data.coverUrl && <Image src={data.coverUrl} alt="" h={160} radius="md" />}
          <Group justify="space-between" align="flex-start" wrap="nowrap">
            <Stack gap={0}>
              <Title order={1}>{data.name}</Title>
              {data.destinations.length > 0 && <Text>{data.destinations.join(' · ')}</Text>}
              <Text size="sm" c="dimmed">{formatDateRange(data.startDate, data.endDate)}</Text>
              <Text size="sm" c="dimmed">{progress(data)}</Text>
            </Stack>
            <OnlineLinkButton to={`/trips/${data.id}/edit`} size="xs" variant="default">{m.dashboard.edit}</OnlineLinkButton>
          </Group>
          <Tab trip={data} />
        </Stack>
      </Container>
      <Box component="nav" aria-label={m.trip.nav} pos="fixed" bottom={0} left={0} right={0} bg="var(--mantine-color-body)" style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
        <Group gap={0} pb="env(safe-area-inset-bottom)" wrap="nowrap">
          {(Object.keys(tabs) as Tab[]).map((k) => (
            // 44px tap targets sharing the width equally; long labels ellipsize on narrow phones.
            <Link
              key={k}
              to={`/trips/${data.id}/${k}`}
              aria-current={k === tab ? 'page' : undefined}
              style={{ flex: 1, minWidth: 0, display: 'block', minHeight: 44, lineHeight: '44px', padding: '0 2px', textAlign: 'center', fontSize: 12, fontWeight: k === tab ? 700 : 400, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
            >
              {m.trip.tabs[k]}
            </Link>
          ))}
        </Group>
      </Box>
    </>
  )
}
