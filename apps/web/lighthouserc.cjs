const port = Number(process.env.PORT || 3000)
const articlePath = process.env.LHCI_ARTICLE_PATH || '/articles/s3-08-advanced-markdown'
const baseUrl = (process.env.LHCI_BASE_URL || `http://127.0.0.1:${port}`).replace(/\/$/, '')
const existingServer = process.env.LHCI_EXISTING_SERVER === 'true'

module.exports = {
  ci: {
    collect: {
      ...(existingServer ? {} : {
        startServerCommand: process.env.LHCI_START_SERVER_COMMAND || 'node scripts/lighthouse-server.mjs',
        startServerReadyPattern: 'Lighthouse server ready',
        startServerReadyTimeout: 120000,
      }),
      url: [`${baseUrl}${articlePath}`],
      numberOfRuns: 1,
      settings: {
        formFactor: 'mobile',
        throttlingMethod: 'provided',
        chromeFlags: process.env.LHCI_CHROME_FLAGS || undefined,
        screenEmulation: { mobile: true, width: 360, height: 800, deviceScaleFactor: 1, disabled: false },
      },
    },
    assert: {
      assertions: {
        'categories:performance': ['error', { minScore: 0.9 }],
        'categories:seo': ['error', { minScore: 0.9 }],
        'categories:accessibility': ['error', { minScore: 0.9 }],
      },
    },
    upload: { target: 'filesystem', outputDir: '.lighthouseci' },
  },
}
