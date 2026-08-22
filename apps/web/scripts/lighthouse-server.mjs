import { spawn } from 'node:child_process'
import http from 'node:http'

const port = Number(process.env.PORT || 3000)
const articlePath = process.env.LHCI_ARTICLE_PATH || '/articles/s3-08-advanced-markdown'
const server = spawn(process.execPath, ['.output/server/index.mjs'], {
  cwd: process.cwd(),
  env: process.env,
  stdio: ['ignore', 'pipe', 'inherit'],
})

let ready = false
let stopped = false

function stop(code = 0) {
  if (stopped) return
  stopped = true
  server.kill()
  process.exit(code)
}

function warmArticle() {
  const request = http.get({
    hostname: '127.0.0.1',
    port,
    path: articlePath,
    headers: { 'Save-Data': 'off' },
  }, response => {
    response.resume()
    response.on('end', () => {
      if (!ready) {
        ready = true
        console.log('Lighthouse server ready')
      }
    })
  })
  request.on('error', error => {
    console.error(error)
    stop(1)
  })
}

server.stdout.on('data', chunk => {
  const output = chunk.toString()
  process.stdout.write(output)
  if (output.includes('Listening on')) warmArticle()
})
server.on('error', error => {
  console.error(error)
  stop(1)
})
server.on('exit', code => {
  if (!stopped) process.exit(code || 1)
})
process.on('SIGINT', () => stop())
process.on('SIGTERM', () => stop())
