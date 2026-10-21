import { expect, test, type Page } from '@playwright/test'
import { fileURLToPath } from 'node:url'

const TICKET = fileURLToPath(new URL('./fixtures/ticket.pdf', import.meta.url))

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

// The persisted query cache is written throttled (1 s); a reload before that would restore, and keep, a stale trip.
const cacheHolds = (page: Page, ...texts: string[]) =>
  page.evaluate(
    (needles) =>
      new Promise<boolean>((resolve) => {
        const open = indexedDB.open('keyval-store')
        open.onerror = () => resolve(false)
        open.onsuccess = () => {
          const db = open.result
          if (!db.objectStoreNames.contains('keyval')) return resolve(false)
          const req = db.transaction('keyval').objectStore('keyval').get('travel-planner-query-cache')
          req.onsuccess = () => {
            if (typeof req.result !== 'string') return resolve(false)
            const queries: { queryKey: unknown[]; state: { data: unknown } }[] = JSON.parse(req.result).clientState.queries
            // One trip query must hold everything: an earlier attempt's trip must not satisfy the check.
            resolve(queries.some((q) => q.queryKey[0] === 'trip' && needles.every((n) => JSON.stringify(q.state.data).includes(n))))
          }
          req.onerror = () => resolve(false)
        }
      }),
    texts,
  )

test('plan a trip, then use it offline', async ({ page, context }, testInfo) => {
  // The database is shared by all attempts of a retried run, so every attempt plans its own trip.
  const tripName = `Guatemala e Belize ${testInfo.retry}-${Date.now()}`
  const tripCard = () => page.getByRole('heading', { name: tripName }).locator('..')
  const today = new Date()
  const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1)

  await page.goto('/')
  await page.getByLabel('Email').fill('smoke@example.it')
  await page.getByRole('textbox', { name: 'Password' }).fill('supersecret1')
  await page.getByRole('button', { name: 'Accedi' }).click()
  await expect(page.getByRole('heading', { name: 'I miei viaggi' })).toBeVisible()

  // The service worker must control the page before it goes offline.
  await page.evaluate(() => navigator.serviceWorker.ready)
  if (!(await page.evaluate(() => !!navigator.serviceWorker.controller))) await page.reload()
  await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true)

  await page.getByRole('button', { name: 'Nuovo viaggio' }).or(page.getByRole('link', { name: 'Nuovo viaggio' })).first().click()
  await page.getByRole('textbox', { name: 'Nome' }).fill(tripName)
  await page.getByRole('textbox', { name: 'Destinazione 1' }).fill('Guatemala')
  await page.getByLabel('Data di inizio').fill(iso(today))
  await page.getByLabel('Data di fine').fill(iso(tomorrow))
  await page.getByRole('button', { name: 'Salva' }).click()
  await expect(page.getByRole('heading', { name: tripName })).toBeVisible()

  await tripCard().getByRole('link', { name: 'Oggi' }).click()
  await page.getByRole('button', { name: /Aggiungi attività/ }).first().click()
  await page.getByLabel('Titolo').fill('Visita al sito')
  await page.getByLabel('Luogo').fill('Tikal')
  await page.getByRole('dialog').getByRole('button', { name: 'Salva' }).click()
  await expect(page.getByText('Tikal')).toBeVisible()

  await page.getByRole('link', { name: 'Documenti' }).click()
  await page.locator('input[type=file]').setInputFiles(TICKET)
  const ticket = page.getByRole('link', { name: 'ticket.pdf', exact: true })
  await expect(ticket).toBeVisible()

  // Open it once (through the service worker, as the new tab would), then keep the trip's files for offline.
  const href = (await ticket.getAttribute('href'))!
  expect(await page.evaluate(async (u) => (await fetch(u)).headers.get('content-type'), href)).toBe('application/pdf')
  await page.getByRole('button', { name: 'Rendi disponibili offline' }).click()
  await expect(page.getByRole('status')).toHaveText('1 file disponibili offline')

  await expect(async () => expect(await cacheHolds(page, tripName, 'Tikal', 'ticket.pdf')).toBe(true)).toPass({ intervals: [500] })

  // Back on the dashboard, with the trip list and the trip cached.
  await page.goto('/')
  await expect(page.getByRole('heading', { name: tripName })).toBeVisible()

  await context.setOffline(true)
  await page.reload()
  await expect(page.getByText(/^Sei offline/)).toBeVisible()
  await expect(page.getByRole('heading', { name: tripName })).toBeVisible()

  await tripCard().getByRole('link', { name: 'Oggi' }).click()
  await expect(page.getByText('Tikal')).toBeVisible()
  await expect(page.getByRole('button', { name: /Aggiungi attività/ }).first()).toBeDisabled()

  await page.getByRole('link', { name: 'Documenti' }).click()
  await expect(page.getByRole('link', { name: 'ticket.pdf', exact: true })).toBeVisible()
  const res = await page.evaluate(async (u) => {
    const r = await fetch(u)
    return { status: r.status, type: r.headers.get('content-type'), head: new TextDecoder().decode((await r.arrayBuffer()).slice(0, 5)) }
  }, href)
  expect(res).toEqual({ status: 200, type: 'application/pdf', head: '%PDF-' })
})
