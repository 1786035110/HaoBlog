$ErrorActionPreference = 'Stop'
if (-not $env:HAOBLOG_BASE_URL) { $env:HAOBLOG_BASE_URL = 'http://localhost:3000' }
node (Join-Path $PSScriptRoot 'smoke-test.mjs')
