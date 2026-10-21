import { ActionIcon, Alert, Anchor, Button, Card, Group, Modal, Stack, Text, Title } from '@mantine/core'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useReorderActivities } from '../api/queries'
import type { Activity, TripDetail } from '../api/types'
import { m } from '../i18n/it'
import { formatDayHeader, todayLocal, tripStatus } from '../lib/dates'
import { groupByDay } from '../lib/itinerary'
import ActivityForm from './ActivityForm'

const t = m.itinerary

interface Editing {
  date: string
  activity?: Activity
}

function ActivityRow({ activity, move, onEdit }: {
  activity: Activity
  move?: { up: boolean; down: boolean; disabled: boolean; onMove: (delta: -1 | 1) => void }
  onEdit: () => void
}) {
  return (
    <Card withBorder padding="sm">
      <Group justify="space-between" wrap="nowrap" align="flex-start">
        <Stack gap={2}>
          <Text fw={500}>
            {activity.startTime && <Text span c="dimmed" mr="xs">{activity.startTime.slice(0, 5)}</Text>}
            {activity.title}
          </Text>
          {activity.location && <Text size="sm">{activity.location}</Text>}
          {activity.mapsLink && (
            <Anchor size="sm" href={activity.mapsLink} target="_blank" rel="noopener">{t.openMap}</Anchor>
          )}
          {activity.notes && <Text size="sm" c="dimmed" style={{ whiteSpace: 'pre-wrap' }}>{activity.notes}</Text>}
        </Stack>
        <Group gap={4} wrap="nowrap">
          {move && (
            <>
              <ActionIcon variant="default" size={36} aria-label={`${t.moveUp}: ${activity.title}`} disabled={!move.up || move.disabled} onClick={() => move.onMove(-1)}>↑</ActionIcon>
              <ActionIcon variant="default" size={36} aria-label={`${t.moveDown}: ${activity.title}`} disabled={!move.down || move.disabled} onClick={() => move.onMove(1)}>↓</ActionIcon>
            </>
          )}
          <Button variant="subtle" size="xs" aria-label={`${t.edit}: ${activity.title}`} onClick={onEdit}>{t.edit}</Button>
        </Group>
      </Group>
    </Card>
  )
}

export default function ItineraryTab({ trip }: { trip: TripDetail }) {
  const [params] = useSearchParams()
  const [editing, setEditing] = useState<Editing>()
  const reorder = useReorderActivities(trip.id)
  const { days, outside } = groupByDay(trip)

  // Fixed at mount: scroll to ?day=, or to today while the trip is in progress.
  const [target] = useState(() => {
    const today = todayLocal()
    return params.get('day') ?? (tripStatus(trip, today) === 'inProgress' ? today : undefined)
  })
  useEffect(() => {
    if (target) document.getElementById(`day-${target}`)?.scrollIntoView()
  }, [target])

  const move = (date: string, list: Activity[], i: number, delta: -1 | 1) => {
    const ids = list.map((a) => a.id)
    ;[ids[i], ids[i + delta]] = [ids[i + delta], ids[i]]
    reorder.mutate({ date, activityIds: ids })
  }

  return (
    <Stack>
      {reorder.isError && <Alert color="red" role="alert">{t.reorderError}</Alert>}
      {days.map((day) => (
        <Stack component="section" key={day.date} id={`day-${day.date}`} gap="xs" style={{ scrollMarginTop: 8 }}>
          <Title order={2} size="h4">{formatDayHeader(day.date, day.dayNumber)}</Title>
          {day.bookings.map((b) => (
            <Card key={b.id} withBorder padding="xs" component={Link} to={`/trips/${trip.id}/bookings`} style={{ textDecoration: 'none', color: 'inherit' }}>
              <Text size="sm">{b.startAt.slice(0, 10) === day.date ? `${b.startAt.slice(11, 16)} ` : ''}{b.title}</Text>
            </Card>
          ))}
          {day.activities.map((a, i) => (
            <ActivityRow
              key={a.id}
              activity={a}
              move={{
                up: i > 0,
                down: i < day.activities.length - 1,
                disabled: reorder.isPending,
                onMove: (delta) => move(day.date, day.activities, i, delta),
              }}
              onEdit={() => setEditing({ date: day.date, activity: a })}
            />
          ))}
          <Button variant="subtle" size="xs" style={{ alignSelf: 'flex-start' }} aria-label={`${t.add} – ${formatDayHeader(day.date, day.dayNumber)}`} onClick={() => setEditing({ date: day.date })}>{t.add}</Button>
        </Stack>
      ))}
      {outside.length > 0 && (
        <Stack component="section" gap="xs">
          <Title order={2} size="h4">{t.outside}</Title>
          {outside.map((a) => (
            <Stack key={a.id} gap={2}>
              <Text size="xs" c="dimmed">{formatDayHeader(a.date)}</Text>
              <ActivityRow activity={a} onEdit={() => setEditing({ date: a.date, activity: a })} />
            </Stack>
          ))}
        </Stack>
      )}
      <Modal opened={!!editing} onClose={() => setEditing(undefined)} title={editing?.activity ? t.editTitle : t.newTitle}>
        {editing && (
          <ActivityForm key={editing.activity?.id ?? editing.date} tripId={trip.id} activity={editing.activity} date={editing.date} onDone={() => setEditing(undefined)} />
        )}
      </Modal>
    </Stack>
  )
}
