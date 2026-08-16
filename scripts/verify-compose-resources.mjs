import fs from 'node:fs'

const input = process.argv[2] && process.argv[2] !== '-' ? fs.readFileSync(process.argv[2], 'utf8') : fs.readFileSync(0, 'utf8')
const config = JSON.parse(input)
const expected = {
  caddy: [64 * 1024 * 1024, 0.2, 64],
  web: [320 * 1024 * 1024, 0.6, 128],
  api: [640 * 1024 * 1024, 1.25, 256],
  'postgres-pgvector': [480 * 1024 * 1024, 0.8, 128],
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
if (!/Xmx384m/.test(java) || !/MaxDirectMemorySize=64m/.test(java)) throw new Error('API JVM bounds changed unexpectedly')
if (Number(config.services.api.mem_limit) <= 384 * 1024 * 1024 + 64 * 1024 * 1024) throw new Error('API has no native memory headroom')
if (config.services.api.environment.HAOBLOG_CONTENT_SCHEDULING_FIXED_DELAY_MS === undefined) throw new Error('scheduler interval is not configurable')
console.log('Compose resource and health boundaries passed')
