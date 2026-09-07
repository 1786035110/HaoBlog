import { defineConfig, devices } from '@playwright/test'

const tlsSpki = process.env.HAOBLOG_TLS_SPKI

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['line'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.HAOBLOG_BASE_URL || 'http://127.0.0.1',
    timezoneId: 'Asia/Shanghai',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{
    name: 'chromium',
    use: {
      ...devices['Desktop Chrome'],
      launchOptions: tlsSpki ? { args: [`--ignore-certificate-errors-spki-list=${tlsSpki}`] } : undefined,
    },
  }],
})
