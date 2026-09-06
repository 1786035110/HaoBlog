import fs from 'node:fs'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'

const input = process.argv[2] && process.argv[2] !== '-' ? fs.readFileSync(process.argv[2], 'utf8') : fs.readFileSync(0, 'utf8')
const config = JSON.parse(input)
const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8' }).trim()
const project = process.argv[3]
const expected = {
  caddy: [64 * 1024 * 1024, 0.2, 64],
  web: [320 * 1024 * 1024, 0.6, 128],
  api: [640 * 1024 * 1024, 1.25, 256],
  'postgres-pgvector': [480 * 1024 * 1024, 0.8, 128],
}

assert.deepEqual(Object.keys(config.services).sort(), Object.keys(expected).sort())
for (const [service, [memory, cpus, pids]] of Object.entries(expected)) {
  const value = config.services[service]
  const app = service === 'api' || service === 'web'
  const grace = service === 'api' || service === 'postgres-pgvector' ? 60 : 30
  assert.equal(Number(value.memswap_limit), memory, `${service}: swap must be disabled`)
  assert.ok([`${grace}s`, grace === 60 ? '1m0s' : '30s'].includes(value.stop_grace_period))
  assert.equal(value.restart, 'unless-stopped')
  assert.deepEqual(value.logging, { driver: 'json-file', options: { 'max-file': '3', 'max-size': '10m' } })
  assert.ok(value.security_opt.includes('no-new-privileges:true'))
  assert.equal(value.ports?.length || 0, service === 'caddy' ? 2 : 0)
  if (service === 'caddy') assert.deepEqual(value.ports.map(p => Number(p.target)).sort((a, b) => a - b), [80, 443])
  if (app) {
    assert.equal(value.read_only, true)
    assert.deepEqual(value.tmpfs, [`/tmp:rw,noexec,nosuid,nodev,size=${service === 'api' ? 33554432 : 16777216},mode=1777`])
    assert.equal(value.volumes?.length || 0, 0)
  } else {
    const mounts = value.volumes.map(m => [m.target, m.type, Boolean(m.read_only)])
    assert.deepEqual(mounts.sort(), (service === 'caddy'
      ? [['/etc/caddy/Caddyfile', 'bind', true], ['/data', 'volume', false], ['/config', 'volume', false]]
      : [['/var/lib/postgresql/data', 'volume', false]]).sort())
  }
  if (!project) continue
  const ids = docker('ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`, '--filter', `label=com.docker.compose.service=${service}`).split(/\s+/).filter(Boolean)
  assert.equal(ids.length, 1, `${service}: exactly one container required`)
  // 仅输出白名单；不能把包含密码的 Config.Env 写入证据。
  const format = '{"memory":{{.HostConfig.Memory}},"swap":{{.HostConfig.MemorySwap}},"cpus":{{.HostConfig.NanoCpus}},"pids":{{.HostConfig.PidsLimit}},"readonly":{{.HostConfig.ReadonlyRootfs}},"tmpfs":{{json (index .HostConfig "Tmpfs")}},"security":{{json .HostConfig.SecurityOpt}},"logging":{{json .HostConfig.LogConfig}},"restart":{{json .HostConfig.RestartPolicy.Name}},"ports":{{json .HostConfig.PortBindings}},"grace":{{.Config.StopTimeout}},"user":{{json .Config.User}},"mounts":{{json .Mounts}},"running":{{.State.Running}},"oom":{{.State.OOMKilled}}}'
  const actual = JSON.parse(docker('inspect', '--format', format, ids[0]))
  assert.equal(actual.memory, memory)
  assert.equal(actual.swap, memory)
  assert.equal(actual.cpus, cpus * 1e9)
  assert.equal(actual.pids, pids)
  assert.equal(actual.grace, grace)
  assert.equal(actual.restart, 'unless-stopped')
  assert.deepEqual(actual.logging, { Type: 'json-file', Config: value.logging.options })
  assert.ok(actual.security.includes('no-new-privileges:true'))
  assert.equal(actual.running, true)
  assert.equal(actual.oom, false)
  const processStatus = docker('exec', ids[0], 'sh', '-c', "grep -E '^(Uid|NoNewPrivs):' /proc/1/status")
  assert.match(processStatus, /NoNewPrivs:\s+1/)
  const processUid = Number(processStatus.match(/Uid:\s+(\d+)/)?.[1])
  if (service !== 'caddy') assert.ok(processUid > 0, `${service}: main process must not run as root`)
  const cgroup = docker('exec', ids[0], 'sh', '-c', 'cat /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.swap.max /sys/fs/cgroup/pids.max /sys/fs/cgroup/cpu.max')
  const [ram, swap, pidLimit, cpuQuota] = cgroup.split('\n')
  assert.equal(Number(ram), memory)
  assert.equal(Number(swap), 0)
  assert.equal(Number(pidLimit), pids)
  const [quota, period] = cpuQuota.split(' ').map(Number)
  assert.equal(quota / period, cpus)
  assert.equal(Object.keys(actual.ports || {}).length, service === 'caddy' ? 2 : 0)
  for (const p of value.ports || []) {
    assert.deepEqual(actual.ports[`${p.target}/${p.protocol}`], [{ HostIp: p.host_ip || '', HostPort: String(p.published) }])
  }
  if (app) {
    assert.equal(actual.readonly, true)
    assert.deepEqual(actual.tmpfs, { '/tmp': value.tmpfs[0].slice(5) })
    assert.notEqual(actual.user, '')
    assert.notEqual(docker('exec', ids[0], 'id', '-u'), '0')
    assert.equal(actual.mounts.filter(m => m.Type !== 'tmpfs').length, 0)
    const key = service === 'api' ? 'JAVA_TOOL_OPTIONS' : 'NODE_OPTIONS'
    assert.equal(docker('exec', ids[0], 'printenv', key), value.environment[key])
    if (service === 'web') assert.equal(docker('exec', ids[0], 'printenv', 'NITRO_SHUTDOWN_TIMEOUT'), '20000')
  } else {
    assert.deepEqual(actual.mounts.map(m => [m.Destination, m.Type, !m.RW]).sort(),
      value.volumes.map(m => [m.target, m.type, Boolean(m.read_only)]).sort())
    for (const mount of actual.mounts.filter(m => m.Type === 'volume')) {
      assert.equal(docker('volume', 'inspect', '--format', '{{index .Labels "com.docker.compose.project"}}', mount.Name), project)
    }
  }
  assert.equal(docker('inspect', '--format', '{{.State.Health.Status}}', ids[0]), 'healthy')
  console.log(JSON.stringify({ service, processUid, processStatus, cgroup, ...actual }))
}

for (const [service, [memory, cpus, pids]] of Object.entries(expected)) {
  const value = config.services?.[service]
  if (!value) throw new Error(`missing service: ${service}`)
  if (Number(value.mem_limit) !== memory) throw new Error(`${service}: mem_limit ${value.mem_limit} !== ${memory}`)
  if (Number(value.cpus) !== cpus) throw new Error(`${service}: cpus ${value.cpus} !== ${cpus}`)
  if (Number(value.pids_limit) !== pids) throw new Error(`${service}: pids_limit ${value.pids_limit} !== ${pids}`)
  if (!value.healthcheck) throw new Error(`${service}: healthcheck is required`)
}

const java = config.services.api.environment.JAVA_TOOL_OPTIONS
assert.equal(java, '-Xms128m -Xmx384m -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError')
assert.equal(config.services.web.environment.NODE_OPTIONS, '--max-old-space-size=192')
assert.equal(config.services.web.environment.NITRO_SHUTDOWN_TIMEOUT, '20000')
assert.match(config.services.api.healthcheck.test.join(' '), /actuator\/health\/liveness/)
assert.match(config.services.web.healthcheck.test.join(' '), /manifest\.webmanifest/)
assert.doesNotMatch(config.services.web.healthcheck.test.join(' '), /3000\/$/)
if (Number(config.services.api.mem_limit) <= 384 * 1024 * 1024 + 64 * 1024 * 1024) throw new Error('API has no native memory headroom')
if (config.services.api.environment.HAOBLOG_CONTENT_SCHEDULING_FIXED_DELAY_MS === undefined) throw new Error('scheduler interval is not configurable')
console.log('Compose resource and health boundaries passed')
