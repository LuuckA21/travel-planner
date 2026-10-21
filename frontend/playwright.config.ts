import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  reporter: 'list',
  retries: process.env.CI ? 1 : 0,
  use: {
    trace: 'retain-on-failure',
    viewport: { width: 390, height: 844 },
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8081',
    // Local runs only: point at a preinstalled Chromium whose revision differs from Playwright's.
    launchOptions: { executablePath: process.env.PW_CHROMIUM_PATH || undefined },
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
})
