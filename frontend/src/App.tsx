import { MantineProvider } from '@mantine/core'
import '@mantine/core/styles.css'
import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router'
import ForgotPasswordPage from './auth/ForgotPasswordPage'
import LoginPage from './auth/LoginPage'
import RequireAuth from './auth/RequireAuth'
import ResetPasswordPage from './auth/ResetPasswordPage'
import SignupPage from './auth/SignupPage'
import VerifyEmailPage from './auth/VerifyEmailPage'
import { m } from './i18n/it'
import { queryClient } from './queryClient'

export default function App() {
  return (
    <MantineProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />
            <Route element={<RequireAuth />}>
              <Route path="/" element={<h1>{m.dashboard.title}</h1>} />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  )
}
