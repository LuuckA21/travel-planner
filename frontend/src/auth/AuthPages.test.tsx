import { MantineProvider } from '@mantine/core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, test, vi } from 'vitest'
import { m } from '../i18n/it'
import ForgotPasswordPage from './ForgotPasswordPage'
import SignupPage from './SignupPage'

const renderPage = (page: ReactNode) =>
  render(
    <MantineProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>{page}</MemoryRouter>
      </QueryClientProvider>
    </MantineProvider>,
  )

afterEach(() => vi.unstubAllGlobals())

test('signup shows its success state on an empty 201', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 201 })))
  const user = userEvent.setup()
  renderPage(<SignupPage />)
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.displayName}`)), 'Ada')
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.email}`)), 'a@b.it')
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.password}`)), 'supersecret1')
  await user.click(screen.getByRole('button', { name: m.auth.signupSubmit }))
  expect(await screen.findByText(m.auth.signupDone)).toBeInTheDocument()
})

test('signup shows Italian copy instead of the server field message', async () => {
  const problem = { errors: { email: 'must be a well-formed email address' } }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(problem), { status: 400 })))
  const user = userEvent.setup()
  renderPage(<SignupPage />)
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.displayName}`)), 'Ada')
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.email}`)), 'a@b.it')
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.password}`)), 'supersecret1')
  await user.click(screen.getByRole('button', { name: m.auth.signupSubmit }))
  expect(await screen.findByText(m.common.invalidField)).toBeInTheDocument()
  expect(screen.queryByText(problem.errors.email)).not.toBeInTheDocument()
})

test('forgot password shows its success state on an empty 202', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 202 })))
  const user = userEvent.setup()
  renderPage(<ForgotPasswordPage />)
  await user.type(screen.getByLabelText(new RegExp(`^${m.auth.email}`)), 'a@b.it')
  await user.click(screen.getByRole('button', { name: m.auth.forgotSubmit }))
  expect(await screen.findByText(m.auth.forgotDone)).toBeInTheDocument()
})
