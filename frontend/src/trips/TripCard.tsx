import { Button, Card, Group, Image, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import type { TripSummary } from '../api/types'
import { m } from '../i18n/it'
import { formatDateRange } from '../lib/dates'
import OnlineLinkButton from '../offline/OnlineLinkButton'

export default function TripCard({ trip, today }: { trip: TripSummary; today?: string }) {
  return (
    <Card withBorder radius="md" padding="md">
      {trip.coverUrl && (
        <Card.Section>
          <Image src={trip.coverUrl} alt="" h={140} />
        </Card.Section>
      )}
      <Title order={3} size="h4" mt="sm">{trip.name}</Title>
      {trip.destinations.length > 0 && <Text size="sm">{trip.destinations.join(' · ')}</Text>}
      <Text size="sm" c="dimmed">{formatDateRange(trip.startDate, trip.endDate)}</Text>
      <Group mt="sm" gap="xs">
        {today && (
          <Button component={Link} to={`/trips/${trip.id}/itinerary?day=${today}`} size="xs">{m.dashboard.today}</Button>
        )}
        <OnlineLinkButton to={`/trips/${trip.id}/edit`} size="xs" variant="default">{m.dashboard.edit}</OnlineLinkButton>
      </Group>
    </Card>
  )
}
