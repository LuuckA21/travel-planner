import { Alert, Anchor, Button, PasswordInput, Stack } from '@mantine/core'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError, apiFetch, NetworkError } from '../api/client'
import { m } from '../i18n/it'
import AuthLayout from './AuthLayout'

export default function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token')
  const [password, setPassword] = useState('')
  const reset = useMutation({
    mutationFn: () => apiFetch<void>('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, password }) }),
  })
  const fieldError = reset.error instanceof ApiError ? reset.error.fieldErrors?.password : undefined
  const error = !token || (reset.error instanceof ApiError && reset.error.status === 400 && !fieldError)
    ? m.auth.invalidLink
    : reset.error instanceof NetworkError
      ? m.common.networkError
      : reset.error instanceof ApiError && reset.error.status === 429
        ? m.common.tooManyRequests
        : reset.error && !fieldError && m.common.genericError

  const submit = (e: FormEvent) => {
    e.preventDefault()
    reset.mutate()
  }

  return (
    <AuthLayout title={m.auth.resetTitle}>
      {reset.isSuccess ? (
        <Stack>
          <Alert color="green">{m.auth.resetDone}</Alert>
          <Anchor component={Link} to="/login">{m.auth.loginLink}</Anchor>
        </Stack>
      ) : (
        <form onSubmit={submit}>
          <Stack>
            {error && <Alert color="red" role="alert">{error}</Alert>}
            <PasswordInput label={m.auth.newPassword} description={m.auth.passwordHint} autoComplete="new-password" required minLength={10} maxLength={128} value={password} error={fieldError} onChange={(e) => setPassword(e.currentTarget.value)} />
            <Button type="submit" loading={reset.isPending} disabled={!token}>{m.auth.resetSubmit}</Button>
            <Anchor component={Link} to="/login" size="sm">{m.auth.loginLink}</Anchor>
          </Stack>
        </form>
      )}
    </AuthLayout>
  )
}
