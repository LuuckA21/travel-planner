import { Alert, Anchor, Loader, Stack } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { apiFetch } from '../api/client'
import { m } from '../i18n/it'
import AuthLayout from './AuthLayout'

export default function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token')
  // A query (not an effect) so StrictMode's double mount doesn't burn the single-use token twice.
  const verify = useQuery({
    queryKey: ['verify-email', token],
    queryFn: async () => {
      await apiFetch<void>('/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) })
      return true
    },
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
  })

  return (
    <AuthLayout title={m.auth.verifyTitle}>
      <Stack>
        {verify.isSuccess && <Alert color="green">{m.auth.verified}</Alert>}
        {(verify.isError || !token) && <Alert color="red" role="alert">{m.auth.invalidLink}</Alert>}
        {token && verify.isPending && <Loader />}
        <Anchor component={Link} to="/login">{m.auth.loginLink}</Anchor>
      </Stack>
    </AuthLayout>
  )
}
