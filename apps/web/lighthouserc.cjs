const port = Number(process.env.PORT || 3000)
const articlePath = process.env.LHCI_ARTICLE_PATH || '/articles/s3-08-advanced-markdown'
const baseUrl = (process.env.LHCI_BASE_URL || `http://127.0.0.1:${port}`).replace(/\/$/, '')
const existingServer = process.env.LHCI_EXISTING_SERVER === 'true'
const noindexAudit = process.env.LHCI_NOINDEX === 'true'
const numberOfRuns = Number(process.env.LHCI_NUMBER_OF_RUNS || 3)
const chromePort = Number(process.env.LHCI_CHROME_PORT || 0)

module.exports = {
  ci: {
    collect: {
      ...(existingServer ? {} : {
        startServerCommand: process.env.LHCI_START_SERVER_COMMAND || 'node scripts/lighthouse-server.mjs',
        startServerReadyPattern: 'Lighthouse server ready',
        startServerReadyTimeout: 120000,
      }),
      url: [`${baseUrl}${articlePath}`],
      // 移动端 Lighthouse 单次采样抖动明显，使用聚合结果避免偶发误报；性能门槛仍保持 0.90。
      numberOfRuns,
      settings: {
        formFactor: 'mobile',
        throttlingMethod: 'provided',
        ...(chromePort > 0 && { port: chromePort }),
        ...(noindexAudit && { ignoreStatusCode: true }),
        chromeFlags: process.env.LHCI_CHROME_FLAGS || undefined,
        screenEmulation: { mobile: true, width: 360, height: 800, deviceScaleFactor: 1, disabled: false },
      },
    },
    assert: {
      assertions: {
        'categories:performance': ['error', { minScore: 0.9 }],
        ...(!noindexAudit && { 'categories:seo': ['error', { minScore: 0.9 }] }),
        'categories:accessibility': ['error', { minScore: 0.9 }],
      },
    },
    upload: { target: 'filesystem', outputDir: '.lighthouseci' },
  },
}
