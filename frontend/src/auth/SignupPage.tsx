import { Alert, Anchor, Button, PasswordInput, Stack, TextInput } from '@mantine/core'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, apiFetch, NetworkError } from '../api/client'
import { m } from '../i18n/it'
import AuthLayout from './AuthLayout'

function errorText(e: unknown): string {
  if (e instanceof NetworkError) return m.common.networkError
  if (e instanceof ApiError) {
    if (e.status === 403 && e.code === 'REGISTRATION_CLOSED') return m.auth.registrationClosed
    if (e.status === 409) return m.auth.emailExists
    if (e.status === 429) return m.common.tooManyRequests
  }
  return m.common.genericError
}

export default function SignupPage() {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const signup = useMutation({
    mutationFn: () =>
      apiFetch<void>('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, displayName }) }),
  })
  const fieldErrors = signup.error instanceof ApiError ? signup.error.fieldErrors : undefined

  const submit = (e: FormEvent) => {
    e.preventDefault()
    signup.mutate()
  }

  return (
    <AuthLayout title={m.auth.signupTitle}>
      {signup.isSuccess ? (
        <Stack>
          <Alert color="green">{m.auth.signupDone}</Alert>
          <Anchor component={Link} to="/login">{m.auth.loginLink}</Anchor>
        </Stack>
      ) : (
        <form onSubmit={submit}>
          <Stack>
            {signup.error && !fieldErrors && <Alert color="red" role="alert">{errorText(signup.error)}</Alert>}
            <TextInput label={m.auth.displayName} autoComplete="name" required maxLength={80} value={displayName} error={fieldErrors?.displayName} onChange={(e) => setDisplayName(e.currentTarget.value)} />
            <TextInput label={m.auth.email} type="email" autoComplete="email" required value={email} error={fieldErrors?.email} onChange={(e) => setEmail(e.currentTarget.value)} />
            <PasswordInput label={m.auth.password} description={m.auth.passwordHint} autoComplete="new-password" required minLength={10} maxLength={128} value={password} error={fieldErrors?.password} onChange={(e) => setPassword(e.currentTarget.value)} />
            <Button type="submit" loading={signup.isPending}>{m.auth.signupSubmit}</Button>
            <Anchor component={Link} to="/login" size="sm">{m.auth.loginLink}</Anchor>
          </Stack>
        </form>
      )}
    </AuthLayout>
  )
}
