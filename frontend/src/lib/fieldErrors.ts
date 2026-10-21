import { ApiError } from '../api/client'
import { m } from '../i18n/it'

// Server validation messages are English: show Italian copy per field, a generic one unless the form knows better.
export function fieldErrors(error: unknown, known: Record<string, string> = {}): Record<string, string> {
  const errors = error instanceof ApiError ? (error.fieldErrors ?? {}) : {}
  return Object.fromEntries(Object.keys(errors).map((k) => [k, known[k] ?? m.common.invalidField]))
}
