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
import { queryClient } from './queryClient'
import DashboardPage from './trips/DashboardPage'
import BookingDetail from './trips/BookingDetail'
import TripFormPage from './trips/TripFormPage'
import TripPage from './trips/TripPage'

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
              <Route path="/" element={<DashboardPage />} />
              <Route path="/trips/new" element={<TripFormPage />} />
              <Route path="/trips/:id/edit" element={<TripFormPage />} />
              <Route path="/trips/:id/bookings/:bookingId" element={<BookingDetail />} />
              <Route path="/trips/:id/:tab?" element={<TripPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </MantineProvider>
  )
}
