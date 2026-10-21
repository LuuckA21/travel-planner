import { Alert, Anchor, Button, Stack, TextInput } from '@mantine/core'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, apiFetch, NetworkError } from '../api/client'
import { m } from '../i18n/it'
import AuthLayout from './AuthLayout'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const forgot = useMutation({
    mutationFn: () => apiFetch<void>('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) }),
  })
  const error = forgot.error instanceof NetworkError
    ? m.common.networkError
    : forgot.error instanceof ApiError && forgot.error.status === 429
      ? m.common.tooManyRequests
      : forgot.error && m.common.genericError

  const submit = (e: FormEvent) => {
    e.preventDefault()
    forgot.mutate()
  }

  return (
    <AuthLayout title={m.auth.forgotTitle}>
      {forgot.isSuccess ? (
        <Stack>
          <Alert color="green">{m.auth.forgotDone}</Alert>
          <Anchor component={Link} to="/login">{m.auth.loginLink}</Anchor>
        </Stack>
      ) : (
        <form onSubmit={submit}>
          <Stack>
            {error && <Alert color="red" role="alert">{error}</Alert>}
            <TextInput label={m.auth.email} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.currentTarget.value)} />
            <Button type="submit" loading={forgot.isPending}>{m.auth.forgotSubmit}</Button>
            <Anchor component={Link} to="/login" size="sm">{m.auth.loginLink}</Anchor>
          </Stack>
        </form>
      )}
    </AuthLayout>
  )
}
