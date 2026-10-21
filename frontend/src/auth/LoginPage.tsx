import { Alert, Anchor, Button, PasswordInput, Stack, Text, TextInput } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, apiFetch, NetworkError } from '../api/client'
import type { AuthConfig, Me } from '../api/types'
import { m } from '../i18n/it'
import AuthLayout from './AuthLayout'

function errorText(e: unknown): string {
  if (e instanceof NetworkError) return m.common.networkError
  if (e instanceof ApiError) {
    if (e.status === 401) return m.auth.wrongCredentials
    if (e.status === 403 && e.code === 'EMAIL_NOT_VERIFIED') return m.auth.notVerified
    if (e.status === 429) return m.common.tooManyRequests
  }
  return m.common.genericError
}

export default function LoginPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const config = useQuery({ queryKey: ['auth-config'], queryFn: () => apiFetch<AuthConfig>('/auth/config'), retry: false })
  const login = useMutation({
    mutationFn: () => apiFetch<Me>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
    onSuccess: (me) => {
      queryClient.setQueryData(['me'], me)
      navigate('/')
    },
  })
  const resend = useMutation({
    mutationFn: () => apiFetch<void>('/auth/resend-verification', { method: 'POST', body: JSON.stringify({ email }) }),
  })
  const notVerified = login.error instanceof ApiError && login.error.code === 'EMAIL_NOT_VERIFIED'

  const submit = (e: FormEvent) => {
    e.preventDefault()
    resend.reset()
    login.mutate()
  }

  return (
    <AuthLayout title={m.auth.loginTitle}>
      <form onSubmit={submit}>
        <Stack>
          {login.error && (
            <Alert color="red" role="alert">
              {errorText(login.error)}
              {notVerified && (
                <>
                  {' '}
                  <Anchor component="button" type="button" onClick={() => resend.mutate()}>{m.auth.resend}</Anchor>
                </>
              )}
            </Alert>
          )}
          {resend.isSuccess && <Alert color="green">{m.auth.resendDone}</Alert>}
          <TextInput label={m.auth.email} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.currentTarget.value)} />
          <PasswordInput label={m.auth.password} autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.currentTarget.value)} />
          <Button type="submit" loading={login.isPending}>{m.auth.loginSubmit}</Button>
          <Text size="sm"><Anchor component={Link} to="/forgot-password">{m.auth.forgotLink}</Anchor></Text>
          {config.data?.registrationOpen && (
            <Text size="sm"><Anchor component={Link} to="/signup">{m.auth.signupLink}</Anchor></Text>
          )}
        </Stack>
      </form>
    </AuthLayout>
  )
}
