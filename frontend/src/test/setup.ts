import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom lacks matchMedia, which Mantine's color scheme handling needs.
window.matchMedia ??= (() => ({ matches: false, addEventListener() {}, removeEventListener() {} })) as never

// Vitest globals are off, so RTL's automatic cleanup does not register.
afterEach(cleanup)
