import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App'
import { m } from './i18n/it'

test('renders the dashboard placeholder heading', () => {
  render(<App />)
  expect(screen.getByRole('heading', { name: m.dashboard.title })).toBeInTheDocument()
})
