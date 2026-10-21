import { afterEach, expect, test, vi } from 'vitest'
import { documentTarget } from './documents'

afterEach(() => {
  vi.restoreAllMocks()
  delete (navigator as { standalone?: boolean }).standalone
})

test('a normal browser tab opens documents in a new tab', () => {
  expect(documentTarget()).toBe('_blank')
})

test('the installed app opens documents in the same window, where its offline cache and session apply', () => {
  vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({ matches: q === '(display-mode: standalone)' }) as MediaQueryList)
  expect(documentTarget()).toBe('_self')
})

test('an iOS Home Screen app (navigator.standalone) opens documents in the same window', () => {
  Object.defineProperty(navigator, 'standalone', { value: true, configurable: true })
  expect(documentTarget()).toBe('_self')
})
