import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'

const action = process.argv[2] || 'preflight'
const project = process.env.HAOBLOG_CANDIDATE_PROJECT || ''
const envFile = process.env.HAOBLOG_CANDIDATE_ENV_FILE || ''
const override = process.env.HAOBLOG_CANDIDATE_OVERRIDE || ''
const candidates = {
  A: { api: process.env.HAOBLOG_A_API_IMAGE, web: process.env.HAOBLOG_A_WEB_IMAGE },
  B: { api: process.env.HAOBLOG_B_API_IMAGE, web: process.env.HAOBLOG_B_WEB_IMAGE },
}

function command(file, args, options = {}) {
  const result = spawnSync(file, args, { encoding: 'utf8', ...options })
  if (result.status !== 0) throw new Error(`${file} ${args.join(' ')} failed: ${(result.stderr || '').trim()}`)
  return (result.stdout || '').trim()
}

const docker = (...args) => command('docker', args)
const composePrefix = spawnSync('docker', ['compose', 'version'], { stdio: 'ignore' }).status === 0
  ? ['docker', ['compose']]
  : ['docker-compose', []]

function validateProject() {
  assert.match(project, /^haoblog-s6-[a-z0-9-]+$/, 'dedicated HAOBLOG_CANDIDATE_PROJECT is required')
  assert.ok(envFile, 'HAOBLOG_CANDIDATE_ENV_FILE is required')
}

function compose(args, images = {}) {
  const base = [...composePrefix[1], '-p', project, '--env-file', envFile, '-f', 'infra/compose/compose.prod.yml']
  if (override) base.push('-f', override)
  return command(composePrefix[0], [...base, ...args], {
    env: { ...process.env, HAOBLOG_API_IMAGE: images.api, HAOBLOG_WEB_IMAGE: images.web },
  })
}

function imageIdentity(image) {
  assert.ok(image, 'candidate image is required')
  const value = JSON.parse(docker('image', 'inspect', '--format', '{{json .}}', image))
  const labels = value.Config.Labels || {}
  for (const key of ['io.haoblog.revision', 'io.haoblog.build-input', 'io.haoblog.config-version', 'io.haoblog.flyway-version']) {
    assert.ok(labels[key] && labels[key] !== 'unknown', `${image}: missing ${key}`)
  }
  assert.equal(labels['io.haoblog.flyway-version'], '17', `${image}: incompatible Flyway version`)
  return { image, id: value.Id, size: value.Size, labels }
}

function projectObjects() {
  const containers = docker('ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`).split(/\s+/).filter(Boolean)
  const volumes = docker('volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`).split(/\s+/).filter(Boolean)
  for (const id of containers) assert.equal(docker('inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', id), project)
  for (const name of volumes) assert.equal(docker('volume', 'inspect', '--format', '{{index .Labels "com.docker.compose.project"}}', name), project)
  return { containers, volumes }
}

function disk() {
  const line = docker('run', '--rm', '--label', `io.haoblog.acceptance.project=${project}`, 'caddy:2.10.0', 'sh', '-c', "df -Pk / | tail -1")
  const parts = line.trim().split(/\s+/)
  const availableGiB = Number(parts[3]) / 1024 / 1024
  const usedPercent = Number(parts[4].replace('%', ''))
  return diskStatus(availableGiB, usedPercent)
}

function diskStatus(availableGiB, usedPercent) {
  assert.ok(availableGiB >= 10, `Docker disk has only ${availableGiB.toFixed(1)} GiB free`)
  return { availableGiB: Number(availableGiB.toFixed(1)), usedPercent, warning: usedPercent >= 75 ? 'usage is at or above 75%' : null }
}

function preflight(target) {
  validateProject()
  assert.ok(candidates[target], 'target must be A or B')
  const identity = { api: imageIdentity(candidates[target].api), web: imageIdentity(candidates[target].web) }
  assert.notEqual(identity.api.id, identity.web.id, 'API and Web must be separate images')
  const other = target === 'A' ? 'B' : 'A'
  for (const service of ['api', 'web']) {
    const otherImage = candidates[other][service]
    if (otherImage && spawnSync('docker', ['image', 'inspect', otherImage], { stdio: 'ignore' }).status === 0) {
      assert.notEqual(identity[service].id, imageIdentity(otherImage).id, `${service}: A and B must be distinct images`)
    }
  }
  compose(['config', '--quiet'], candidates[target])
  return { project, target, disk: disk(), objects: projectObjects(), identity }
}

function container(service) {
  const ids = docker('ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`, '--filter', `label=com.docker.compose.service=${service}`).split(/\s+/).filter(Boolean)
  assert.equal(ids.length, 1, `${service}: exactly one container is required`)
  return ids[0]
}

async function waitHealthy(service, timeoutSeconds = 180) {
  const id = container(service)
  const deadline = Date.now() + timeoutSeconds * 1000
  while (Date.now() < deadline) {
    const state = docker('inspect', '--format', '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}', id)
    if (state === 'running healthy') return
    if (state.startsWith('exited') || state.startsWith('dead')) throw new Error(`${service} failed: ${state}`)
    await new Promise(resolve => setTimeout(resolve, 2000))
  }
  throw new Error(`${service} health timeout`)
}

async function activate(target) {
  const images = candidates[target]
  preflight(target)
  compose(['stop', 'web', 'api'], images)
  compose(['up', '-d', '--no-deps', 'api'], images)
  await waitHealthy('api')
  compose(['up', '-d', '--no-deps', 'web'], images)
  await waitHealthy('web')
  const active = { api: docker('inspect', '--format', '{{.Image}}', container('api')), web: docker('inspect', '--format', '{{.Image}}', container('web')) }
  assert.equal(active.api, imageIdentity(images.api).id)
  assert.equal(active.web, imageIdentity(images.web).id)
  return { project, target, active }
}

async function switchCandidate() {
  validateProject()
  const target = process.argv[3]
  const fallback = process.argv[4]
  try {
    return await activate(target)
  } catch (error) {
    if (!fallback || !candidates[fallback]) throw error
    const restored = await activate(fallback)
    throw new Error(`${error.message}; restored ${fallback}: ${JSON.stringify(restored.active)}`)
  }
}

async function countLogs(id) {
  return await new Promise((resolve, reject) => {
    const child = spawn('docker', ['logs', id], { stdio: ['ignore', 'pipe', 'pipe'] })
    let bytes = 0
    let tail = ''
    for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => { bytes += chunk.length; tail = (tail + chunk).slice(-256) })
    child.on('error', reject)
    child.on('close', code => code === 0 ? resolve({ bytes, tail }) : reject(new Error(`docker logs failed: ${code}`)))
  })
}

async function rotation() {
  validateProject()
  const name = `${project}-log-rotation`
  assert.equal(docker('ps', '-aq', '--filter', `name=^/${name}$`), '', `${name} already exists`)
  const id = docker('run', '-d', '--name', name, '--label', `io.haoblog.acceptance.project=${project}`, '--log-driver', 'json-file', '--log-opt', 'max-size=10m', '--log-opt', 'max-file=3', 'node:24-bookworm-slim', 'node', '-e', "console.log('ROTATE-BEGIN');for(let i=0;i<36000;i++)console.log('X'.repeat(1024));console.log('ROTATE-END')")
  docker('wait', id)
  const config = JSON.parse(docker('inspect', '--format', '{{json .HostConfig.LogConfig}}', id))
  assert.deepEqual(config, { Type: 'json-file', Config: { 'max-file': '3', 'max-size': '10m' } })
  const logs = await countLogs(id)
  assert.match(logs.tail, /ROTATE-END/)
  assert.ok(logs.bytes < 32 * 1024 * 1024, `retained ${logs.bytes} bytes; rotation did not bound logs`)
  docker('rm', id)
  return { project, config, retainedBytes: logs.bytes, endMarker: true }
}

if (action === 'self-test') {
  assert.match('haoblog-s6-050607-demo', /^haoblog-s6-[a-z0-9-]+$/)
  assert.equal('abc_-.123'.match(/^[A-Za-z0-9._-]{1,64}$/)?.[0], 'abc_-.123')
  assert.throws(() => diskStatus(9.9, 10), /only 9.9 GiB free/)
  assert.equal(diskStatus(20, 75).warning, 'usage is at or above 75%')
  console.log('local candidate self-test passed')
} else if (action === 'preflight') console.log(JSON.stringify(preflight(process.argv[3] || 'B'), null, 2))
else if (action === 'status') { validateProject(); console.log(JSON.stringify({ project, objects: projectObjects() }, null, 2)) }
else if (action === 'switch') console.log(JSON.stringify(await switchCandidate(), null, 2))
else if (action === 'rotation') console.log(JSON.stringify(await rotation(), null, 2))
else throw new Error('usage: local-candidate.mjs self-test|preflight A|B|status|switch A|B [fallback]|rotation')
