import { ActionIcon, Alert, Button, Card, Checkbox, Group, Progress, Stack, Text, TextInput } from '@mantine/core'
import { useState, type FormEvent } from 'react'
import { NetworkError } from '../api/client'
import { useAddItem, useDeleteChecklist, useDeleteItem, usePatchItem, useSaveChecklist } from '../api/queries'
import type { Checklist, ChecklistItem, TripDetail } from '../api/types'
import { m } from '../i18n/it'

const t = m.checklists

// One flag drives every mutating control (Task 24 wires it to the online state).
interface Ctl {
  tripId: string
  disabled: boolean
  fail: (err: unknown) => void
}

const errorText = (err: unknown) => (err instanceof NetworkError ? m.common.networkError : m.common.genericError)

function ErrorAlert({ error }: { error: unknown }) {
  return error != null ? <Alert color="red" role="alert">{errorText(error)}</Alert> : null
}

function ItemRow({ item, tripId, disabled, fail }: Ctl & { item: ChecklistItem }) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(item.text)
  const patch = usePatchItem(tripId)
  const del = useDeleteItem(tripId)

  const save = async (e: FormEvent) => {
    e.preventDefault()
    const next = text.trim()
    if (!next) return
    if (next !== item.text) {
      try {
        await patch.mutateAsync({ id: item.id, text: next })
      } catch (err) {
        return fail(err)
      }
    }
    setEditing(false)
  }

  if (editing) {
    return (
      <form onSubmit={(e) => void save(e)}>
        <Group gap="xs" wrap="nowrap">
          <TextInput aria-label={t.editItem} value={text} onChange={(e) => setText(e.currentTarget.value)} maxLength={300} style={{ flex: 1 }} data-autofocus autoFocus />
          <Button type="submit" size="compact-sm" loading={patch.isPending} disabled={disabled}>{t.save}</Button>
          <Button variant="default" size="compact-sm" onClick={() => { setText(item.text); setEditing(false) }}>{t.cancel}</Button>
        </Group>
      </form>
    )
  }

  return (
    <Group gap="xs" wrap="nowrap" justify="space-between">
      <Checkbox
        label={item.text}
        checked={item.done}
        disabled={disabled}
        onChange={(e) => patch.mutate({ id: item.id, done: e.currentTarget.checked }, { onError: fail })}
        styles={{ label: { overflowWrap: 'anywhere' } }}
      />
      <Group gap={4} wrap="nowrap">
        <Button variant="subtle" size="compact-xs" disabled={disabled} aria-label={`${t.editItem} ${item.text}`} onClick={() => { setText(item.text); setEditing(true) }}>✎</Button>
        <ActionIcon variant="subtle" color="red" size="sm" disabled={disabled} loading={del.isPending} aria-label={`${t.deleteItem} ${item.text}`} onClick={() => del.mutate(item.id, { onError: fail })}>×</ActionIcon>
      </Group>
    </Group>
  )
}

function ChecklistCard({ list, tripId, disabled }: Omit<Ctl, 'fail'> & { list: Checklist }) {
  const [error, setError] = useState<unknown>()
  const [renaming, setRenaming] = useState(false)
  const [name, setName] = useState(list.name)
  const [confirm, setConfirm] = useState(false)
  const [newItem, setNewItem] = useState('')
  const save = useSaveChecklist(tripId)
  const del = useDeleteChecklist(tripId)
  const add = useAddItem(tripId)
  const fail = (err: unknown) => { setError(err); setConfirm(false) }

  const done = list.items.filter((i) => i.done).length
  const total = list.items.length

  const rename = async (e: FormEvent) => {
    e.preventDefault()
    const next = name.trim()
    if (!next) return
    setError(undefined)
    if (next !== list.name) {
      try {
        await save.mutateAsync({ id: list.id, name: next })
      } catch (err) {
        return fail(err)
      }
    }
    setRenaming(false)
  }

  const addItem = async (e: FormEvent) => {
    e.preventDefault()
    const text = newItem.trim()
    if (!text || add.isPending) return
    setError(undefined)
    try {
      await add.mutateAsync({ checklistId: list.id, text })
      setNewItem('')
    } catch (err) {
      fail(err)
    }
  }

  const remove = async () => {
    setError(undefined)
    try {
      await del.mutateAsync(list.id)
    } catch (err) {
      fail(err)
    }
  }

  return (
    <Card withBorder padding="sm">
      <Stack gap="xs">
        <ErrorAlert error={error} />
        {renaming ? (
          <form onSubmit={(e) => void rename(e)}>
            <Group gap="xs" wrap="nowrap">
              <TextInput aria-label={t.rename} value={name} onChange={(e) => setName(e.currentTarget.value)} maxLength={80} style={{ flex: 1 }} autoFocus />
              <Button type="submit" size="compact-sm" loading={save.isPending} disabled={disabled}>{t.save}</Button>
              <Button variant="default" size="compact-sm" onClick={() => { setName(list.name); setRenaming(false) }}>{t.cancel}</Button>
            </Group>
          </form>
        ) : (
          <Group justify="space-between" wrap="nowrap">
            <Text fw={600} style={{ overflowWrap: 'anywhere' }}>{list.name}</Text>
            <Group gap="xs" wrap="nowrap">
              <Button variant="subtle" size="compact-sm" disabled={disabled} onClick={() => { setName(list.name); setRenaming(true) }}>{t.rename}</Button>
              <Button variant="subtle" color="red" size="compact-sm" disabled={disabled} loading={del.isPending} onClick={confirm ? () => void remove() : () => setConfirm(true)}>
                {confirm ? t.deleteConfirm : t.delete}
              </Button>
            </Group>
          </Group>
        )}
        <Group gap="xs" wrap="nowrap">
          <Progress value={total ? (done / total) * 100 : 0} aria-label={t.progress} style={{ flex: 1 }} />
          <Text size="sm" c="dimmed">{done}/{total}</Text>
        </Group>
        {list.items.map((i) => <ItemRow key={i.id} item={i} tripId={tripId} disabled={disabled} fail={fail} />)}
        <form onSubmit={(e) => void addItem(e)}>
          <TextInput aria-label={t.newItem} placeholder={t.newItem} value={newItem} onChange={(e) => setNewItem(e.currentTarget.value)} maxLength={300} disabled={disabled} />
        </form>
      </Stack>
    </Card>
  )
}

export default function ChecklistsTab({ trip }: { trip: TripDetail }) {
  const disabled = false
  const [name, setName] = useState('')
  const [error, setError] = useState<unknown>()
  const save = useSaveChecklist(trip.id)

  const create = async (e: FormEvent) => {
    e.preventDefault()
    const next = name.trim()
    if (!next) return
    setError(undefined)
    try {
      await save.mutateAsync({ name: next })
      setName('')
    } catch (err) {
      setError(err)
    }
  }

  return (
    <Stack>
      {trip.checklists.length === 0 && <Text c="dimmed">{t.empty}</Text>}
      {trip.checklists.map((c) => <ChecklistCard key={c.id} list={c} tripId={trip.id} disabled={disabled} />)}
      <ErrorAlert error={error} />
      <form onSubmit={(e) => void create(e)}>
        <Group gap="xs" wrap="nowrap" align="flex-end">
          <TextInput label={t.newList} value={name} onChange={(e) => setName(e.currentTarget.value)} maxLength={80} disabled={disabled} style={{ flex: 1 }} />
          <Button type="submit" loading={save.isPending} disabled={disabled}>{t.addList}</Button>
        </Group>
      </form>
    </Stack>
  )
}
