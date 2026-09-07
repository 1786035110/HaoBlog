import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { execFile, spawnSync } from 'node:child_process'
import { createWriteStream, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { resolve, sep } from 'node:path'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const argv = process.argv.slice(2).filter((value, index) => value !== '--' || index > 0)
const action = argv[0] || 'self-test'
const mode = argv[1] || 'preflight'
const project = process.env.HAOBLOG_S6_PROJECT || ''
const runId = process.env.HAOBLOG_S6_RUN_ID || ''
const baseUrl = process.env.HAOBLOG_S6_BASE_URL || ''
const evidenceDir = process.env.HAOBLOG_S6_EVIDENCE_DIR || ''
const services = ['caddy', 'web', 'api', 'postgres-pgvector']
const limits = {
  caddy: { memory: 64 * 1024 ** 2, nanoCpus: 200_000_000, pids: 64 },
  web: { memory: 320 * 1024 ** 2, nanoCpus: 600_000_000, pids: 128 },
  api: { memory: 640 * 1024 ** 2, nanoCpus: 1_250_000_000, pids: 256 },
  'postgres-pgvector': { memory: 480 * 1024 ** 2, nanoCpus: 800_000_000, pids: 128 },
}

function run(file, args, options = {}) {
  const result = spawnSync(file, args, { encoding: 'utf8', timeout: 30_000, maxBuffer: 32 * 1024 ** 2, ...options })
  if (result.status !== 0) throw new Error(`${file} ${args.join(' ')} failed (${result.status}): ${(result.stderr || result.stdout || '').trim().slice(-1200)}`)
  return (result.stdout || '').trim()
}

const docker = (...args) => run('docker', args)

function validateConfig({ destructive = false } = {}) {
  assert.ok(mode === 'preflight' || mode === 'formal', 'mode must be preflight or formal')
  assert.match(project, /^haoblog-s6-[a-z0-9-]+$/, 'HAOBLOG_S6_PROJECT must identify a dedicated haoblog-s6-* project')
  assert.match(runId, /^s6-i4-[0-9]{8}-[0-9]{4,6}(?:-[a-z0-9-]+)?$/, 'HAOBLOG_S6_RUN_ID has an invalid format')
  const origin = new URL(baseUrl)
  assert.equal(origin.href, origin.origin + '/', 'HAOBLOG_S6_BASE_URL must be an origin without path, query, fragment or credentials')
  assert.ok(['http:', 'https:'].includes(origin.protocol), 'target must use HTTP or HTTPS')
  const privateHost = origin.hostname === 'localhost' || origin.hostname === '127.0.0.1'
    || /^10\./.test(origin.hostname) || /^192\.168\./.test(origin.hostname)
    || /^172\.(1[6-9]|2\d|3[01])\./.test(origin.hostname) || origin.hostname.endsWith('.local')
  assert.ok(privateHost, 'public target host is forbidden')
  if (mode === 'formal') {
    assert.equal(origin.protocol, 'https:', 'formal mode requires HTTPS')
    assert.notEqual(process.env.HAOBLOG_S6_ALLOW_SELF_SIGNED, 'true', 'formal mode forbids disabling TLS validation')
    assert.notEqual(process.env.NODE_TLS_REJECT_UNAUTHORIZED, '0', 'formal mode requires a trusted task CA')
  } else if (process.env.HAOBLOG_S6_ALLOW_SELF_SIGNED === 'true') {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0'
  }
  if (destructive) assert.equal(process.env.HAOBLOG_S6_CONFIRM, `${project}|${origin.origin}`, 'HAOBLOG_S6_CONFIRM does not match project and origin')
  return origin
}

function prepareEvidence() {
  const expectedRoot = resolve('docs/target/阶段六验收记录') + sep
  const target = resolve(evidenceDir)
  assert.ok(target.startsWith(expectedRoot) && target.endsWith(`${sep}${runId}`), 'evidence directory must be docs/target/阶段六验收记录/<run-id>')
  mkdirSync(target, { recursive: true })
  return target
}

function container(service) {
  const ids = docker('ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`, '--filter', `label=com.docker.compose.service=${service}`).split(/\s+/).filter(Boolean)
  assert.equal(ids.length, 1, `${service}: exactly one project container is required`)
  assert.equal(docker('inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', ids[0]), project)
  return ids[0]
}

function inspectProject() {
  const projectIds = docker('ps', '-q', '--filter', `label=com.docker.compose.project=${project}`).split(/\s+/).filter(Boolean)
  assert.equal(projectIds.length, 4, 'the target project must have exactly four running containers')
  const found = {}
  for (const service of services) {
    const id = container(service)
    const item = JSON.parse(docker('inspect', '--format', '{{json .}}', id))
    const expected = limits[service]
    assert.equal(item.State.Status, 'running', `${service} is not running`)
    assert.equal(item.State.Health?.Status, 'healthy', `${service} is not healthy`)
    assert.equal(item.HostConfig.Memory, expected.memory, `${service}: unexpected memory limit`)
    assert.equal(item.HostConfig.MemorySwap, expected.memory, `${service}: container swap must be zero`)
    assert.equal(item.HostConfig.NanoCpus, expected.nanoCpus, `${service}: unexpected CPU limit`)
    assert.equal(item.HostConfig.PidsLimit, expected.pids, `${service}: unexpected PID limit`)
    found[service] = {
      id: item.Id, imageId: item.Image, image: item.Config.Image, restartCount: item.RestartCount,
      memory: item.HostConfig.Memory, memorySwap: item.HostConfig.MemorySwap,
      nanoCpus: item.HostConfig.NanoCpus, pids: item.HostConfig.PidsLimit,
    }
  }
  return { projectIds, services: found }
}

function hostTruth(dbId) {
  const info = JSON.parse(docker('info', '--format', '{{json .}}'))
  const proc = docker('exec', dbId, 'sh', '-c', "awk '/^(MemTotal|MemAvailable|SwapTotal|SwapFree):/{print $1,$2}' /proc/meminfo; printf 'cpus '; getconf _NPROCESSORS_ONLN; printf 'swapmax '; cat /sys/fs/cgroup/memory.swap.max")
  const map = Object.fromEntries(proc.split(/\r?\n/).map(line => line.trim().split(/\s+/)).filter(parts => parts.length === 2))
  return {
    docker: { operatingSystem: info.OperatingSystem, osType: info.OSType, kernelVersion: info.KernelVersion, ncpu: info.NCPU, memTotal: info.MemTotal },
    linux: { cpus: Number(map.cpus), memTotalKiB: Number(map['MemTotal:']), memAvailableKiB: Number(map['MemAvailable:']), swapTotalKiB: Number(map['SwapTotal:']), swapFreeKiB: Number(map['SwapFree:']), dbCgroupSwapMax: map.swapmax },
  }
}

async function preflight() {
  const origin = validateConfig()
  const projectState = inspectProject()
  const truth = hostTruth(projectState.services['postgres-pgvector'].id)
  const allRunning = docker('ps', '-q').split(/\s+/).filter(Boolean)
  const fourOnly = allRunning.length === 4 && allRunning.every(id => projectState.projectIds.includes(id))
  const memoryOk = truth.docker.memTotal >= 1.8 * 1024 ** 3 && truth.docker.memTotal <= 2.2 * 1024 ** 3
    && truth.linux.memTotalKiB >= 1.8 * 1024 ** 2 && truth.linux.memTotalKiB <= 2.2 * 1024 ** 2
  const cpuOk = truth.docker.osType === 'linux' && truth.docker.ncpu === 2 && truth.linux.cpus === 2
  const response = await fetch(new URL('/api/v1/public/site', origin), { signal: AbortSignal.timeout(8000) })
  assert.equal(response.status, 200, 'public site marker request failed')
  const site = await response.json()
  const result = { runId, mode, checkedAt: new Date().toISOString(), origin: origin.origin, projectState, truth, allRunningCount: allRunning.length, formalEligible: fourOnly && memoryOk && cpuOk, site: { title: site.title, description: site.description } }
  if (mode === 'formal') assert.ok(result.formalEligible, 'formal mode requires an actual 2 vCPU / 2 GiB Linux host with only the four target containers')
  return result
}

function uuid(group, n) {
  const hex = n.toString(16).padStart(12, '0')
  return `0199${group.toString(16).padStart(4, '0')}-0000-7000-8000-${hex}`
}

const quote = value => `'${String(value).replaceAll("'", "''")}'`

function advancedMarkdownFixture() {
  const source = readFileSync(resolve('apps/api/src/main/java/io/haoblog/content/application/ArticleDevelopmentSeeder.java'), 'utf8')
  const match = source.match(/private String advancedMarkdown\(\) \{\s*return """\r?\n([\s\S]*?)\r?\n\s*""";/)
  assert.ok(match, 'the existing advanced Markdown fixture was not found')
  const lines = match[1].split(/\r?\n/)
  const indent = Math.min(...lines.filter(line => line.trim()).map(line => line.match(/^\s*/)[0].length))
  return lines.map(line => line.slice(indent)).join('\n').replaceAll('\\\\', '\\')
}

function fixedSeedSql(marker) {
  const now = new Date()
  const lines = ['BEGIN;', `UPDATE site_setting SET description=${quote(marker)}, comments_enabled=true, version=version+1 WHERE site_key='default';`,
    'TRUNCATE TABLE comment, article_preview_token, outbox_event, article_tag, article_revision, article, category, tag, tool, tool_category CASCADE;']
  for (let index = 1; index <= 25; index += 1) {
    lines.push(`INSERT INTO category(id,name,slug,description,sort_order,created_at,updated_at) VALUES('${uuid(1, index)}','分类 ${index}','s6-category-${index}','阶段六合成分类',${index},now(),now());`)
    lines.push(`INSERT INTO tag(id,name,slug,created_at,updated_at) VALUES('${uuid(2, index)}','标签 ${index}','s6-tag-${index}',now(),now());`)
  }
  for (let index = 1; index <= 10; index += 1) lines.push(`INSERT INTO tool_category(id,name,slug,description,sort_order,created_at,updated_at) VALUES('${uuid(3, index)}','工具分类 ${index}','s6-tool-category-${index}','阶段六合成工具分类',${index},now(),now());`)
  const embedded = [
    ['json-format', 'JSON 格式化', 'json-format'], ['base64', 'Base64 编解码', 'base64'],
    ['url-codec', 'URL 编解码', 'url-codec'], ['timestamp', '时间戳转换', 'timestamp'],
    ['regex-test', '正则测试', 'regex-test'],
  ]
  for (let index = 1; index <= 100; index += 1) {
    const fixture = embedded[index - 1]
    lines.push(`INSERT INTO tool(id,category_id,type,status,title,slug,description,url,component_key,tags,sort_order,created_at,updated_at) VALUES('${uuid(4, index)}','${uuid(3, (index - 1) % 10 + 1)}','${fixture ? 'EMBEDDED' : index % 2 ? 'LINK' : 'SHOWCASE'}','ACTIVE',${quote(fixture?.[1] || `阶段六工具 ${index}`)},${quote(fixture?.[2] || `s6-tool-${index}`)},'合成工具目录',${fixture ? 'NULL' : quote(`https://example.test/tools/${index}`)},${fixture ? quote(fixture[0]) : 'NULL'},'["s6","synthetic"]',${index},now(),now());`)
  }
  const advanced = advancedMarkdownFixture()
  const normal = '# 普通中文长文\n\n' + '极夜观测站记录一段稳定、可检索且适合连续阅读的中文内容。'.repeat(1800)
  const nearMiB = '# 接近一 MiB 合法文章\n\n' + '测'.repeat(346000)
  for (let index = 1; index <= 240; index += 1) {
    const status = index <= 200 ? 'PUBLISHED' : index <= 220 ? 'DRAFT' : index <= 230 ? 'ARCHIVED' : 'SCHEDULED'
    const slug = index === 1 ? 's3-08-advanced-markdown' : index === 2 ? 's6-normal-chinese' : index === 3 ? 's6-near-one-mib' : `s6-article-${String(index).padStart(3, '0')}`
    const title = index === 1 ? 'S3-08 高级 Markdown 固定验收文章' : index === 2 ? '阶段六普通中文长文' : index === 3 ? '阶段六接近一 MiB 合法文章' : `阶段六合成文章 ${index}`
    const excerpt = index === 1 ? '覆盖安全高级 Markdown、SSR、TOC、图片和降级路径的阶段三固定验收文章。' : `阶段六合成摘要 ${index}`
    const markdown = index === 1 ? advanced : index === 2 ? normal : index === 3 ? nearMiB : `# ${title}\n\n这是可检索的中文观测记录 ${index}。\n\n${'稳定负载段落。'.repeat(40)}`
    const publishedAt = status === 'PUBLISHED' ? quote(new Date(now.getTime() - index * 60_000).toISOString()) : 'NULL'
    const scheduledAt = status === 'SCHEDULED' ? quote(new Date(now.getTime() + (index - 229) * 86_400_000).toISOString()) : 'NULL'
    lines.push(`INSERT INTO article(id,slug,title,excerpt,markdown_source,status,published_at,scheduled_at,created_at,updated_at,version,category_id,comments_enabled) VALUES('${uuid(5, index)}',${quote(slug)},${quote(title)},${quote(excerpt)},${quote(markdown)},'${status}',${publishedAt},${scheduledAt},now()-interval '${index} minutes',now(),0,'${uuid(1, (index - 1) % 25 + 1)}',${index % 10 !== 0});`)
    if (status === 'PUBLISHED') {
      lines.push(`INSERT INTO article_revision(id,article_id,source_version,title,slug,excerpt,markdown_source,category_snapshot,tag_snapshot,change_reason,created_at) VALUES('${uuid(6, index)}','${uuid(5, index)}',0,${quote(title)},${quote(slug)},${quote(excerpt)},${quote(markdown)},jsonb_build_object('id','${uuid(1, (index - 1) % 25 + 1)}','name','分类 ${(index - 1) % 25 + 1}','slug','s6-category-${(index - 1) % 25 + 1}'),'[]','S6 fixed fixture',now());`)
      lines.push(`UPDATE article SET published_revision_id='${uuid(6, index)}' WHERE id='${uuid(5, index)}';`)
    }
    for (let tag = 0; tag < 3; tag += 1) lines.push(`INSERT INTO article_tag(article_id,tag_id) VALUES('${uuid(5, index)}','${uuid(2, (index + tag - 1) % 25 + 1)}');`)
  }
  const statuses = ['APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'APPROVED', 'PENDING', 'SPAM', 'REJECTED', 'USER_DELETED']
  for (let index = 1; index <= 2000; index += 1) {
    const articleIndex = (index - 1) % 190 + 1
    const status = statuses[Math.floor((index - 1) / 50) % statuses.length]
    lines.push(`INSERT INTO comment(id,article_id,nickname,content,status,ip_hmac,ip_hmac_date,content_fingerprint,delete_token_digest,created_at,updated_at,deleted_at) VALUES('${uuid(7, index)}','${uuid(5, articleIndex)}','访客 ${index}','阶段六合成评论 ${index}','${status}',digest('s6-ip-${index}','sha256'),current_date,digest('s6-content-${index}','sha256'),digest('s6-token-${index}','sha256'),now()-interval '${index} seconds',now(),${status === 'USER_DELETED' ? 'now()' : 'NULL'});`)
  }
  lines.push('COMMIT;')
  return lines.join('\n')
}

function psql(sql) {
  const id = container('postgres-pgvector')
  return run('docker', ['exec', '-i', id, 'sh', '-c', 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], { input: sql })
}

function query(sql) {
  const id = container('postgres-pgvector')
  return docker('exec', id, 'sh', '-c', `psql -X -At -F '|' -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c ${JSON.stringify(sql)}`)
}

async function seed() {
  validateConfig({ destructive: true })
  const before = await preflight()
  const target = prepareEvidence()
  const marker = `S6 acceptance ${runId}`
  psql(fixedSeedSql(marker))
  const counts = query("SELECT status,count(*) FROM article GROUP BY status ORDER BY status; SELECT 'comments',count(*) FROM comment; SELECT 'categories_tags',count(*) FROM category,(SELECT count(*) AS ignored FROM tag) t GROUP BY t.ignored; SELECT 'tags',count(*) FROM tag; SELECT 'tools',count(*) FROM tool; SELECT 'embedded',count(*) FROM tool WHERE type='EMBEDDED'; SELECT 'large_bytes',octet_length(markdown_source) FROM article WHERE slug='s6-near-one-mib';")
  assert.match(counts, /PUBLISHED\|200/)
  assert.match(counts, /DRAFT\|20/)
  assert.match(counts, /ARCHIVED\|10/)
  assert.match(counts, /SCHEDULED\|10/)
  assert.match(counts, /comments\|2000/)
  assert.match(counts, /tools\|100/)
  assert.match(counts, /embedded\|5/)
  const publicSite = await (await fetch(new URL('/api/v1/public/site', baseUrl), { signal: AbortSignal.timeout(8000) })).json()
  assert.equal(publicSite.description, marker)
  const result = { runId, seededAt: new Date().toISOString(), marker, counts: counts.split(/\r?\n/), preflight: before, generatorSha256: createHash('sha256').update(fixedSeedSql('MARKER').replace(/now\(\)/g, 'NOW')).digest('hex') }
  writeFileSync(resolve(target, 'seed-manifest.json'), JSON.stringify(result, null, 2))
  return result
}

function percentile(values, p) {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.max(0, Math.ceil(p * sorted.length) - 1)]
}

function summarize(records) {
  const result = {}
  for (const record of records) {
    const key = `${record.window}:${record.category}:${record.status === 200 ? '200' : record.status === 304 ? '304' : 'other'}`
    const bucket = result[key] ||= { total: 0, success: 0, failure: 0, timeout: 0, aborted: 0, rejected: 0, durations: [] }
    bucket.total += 1
    bucket[record.outcome] = (bucket[record.outcome] || 0) + 1
    if ([429, 503, 504].includes(record.status)) bucket.rejected += 1
    bucket.durations.push(record.durationMs)
  }
  return Object.fromEntries(Object.entries(result).map(([key, value]) => [key, { ...value, p50: percentile(value.durations, .5), p95: percentile(value.durations, .95), p99: percentile(value.durations, .99), max: Math.max(...value.durations), durations: undefined }]))
}

function summarizeResources(samples, warmMs, normalMs, expectedSamples) {
  const normal = samples.filter(sample => sample.offsetMs >= warmMs && sample.offsetMs < warmMs + normalMs)
  assert.ok(normal.length, 'normal resource samples are missing')
  const raw = sample => Object.values(sample.containers).reduce((total, item) => total + Number(item['memory.current']), 0)
  const working = sample => Object.values(sample.containers).reduce((total, item) => total + Math.max(0, Number(item['memory.current']) - Number(item.inactive_file || 0)), 0)
  let pagingSeconds = 0, maxPagingSeconds = 0
  for (let index = 1; index < normal.length; index += 1) {
    const previous = normal[index - 1], current = normal[index]
    const paging = Number(current.host.pswpin) > Number(previous.host.pswpin) || Number(current.host.pswpout) > Number(previous.host.pswpout)
    pagingSeconds = paging ? pagingSeconds + Math.max(1, Math.round((current.offsetMs - previous.offsetMs) / 1000)) : 0
    maxPagingSeconds = Math.max(maxPagingSeconds, pagingSeconds)
  }
  const oomEvents = Math.max(...samples.flatMap(sample => Object.values(sample.containers).map(item => Number(item.oom || 0) + Number(item.oom_kill || 0))))
  const databaseSamples = samples.filter(sample => sample.database).length
  const summary = {
    samples: samples.length,
    databaseSamples,
    rawCgroupMaxBytes: Math.max(...normal.map(raw)),
    workingSetMaxBytes: Math.max(...normal.map(working)),
    hostMemAvailableMinKiB: Math.min(...normal.map(sample => Number(sample.host['MemAvailable:']))),
    diskAvailableMinKiB: Math.min(...normal.map(sample => Number(sample.host.diskAvailableKiB))),
    maxContinuousPagingSeconds: maxPagingSeconds,
    oomEvents,
    recoveryEndWorkingSetBytes: working(samples.at(-1)),
  }
  summary.passed = summary.samples >= expectedSamples - 2 && databaseSamples >= Math.floor(expectedSamples / 5) - 1
    && summary.workingSetMaxBytes <= 1.5 * 1024 ** 3 && summary.hostMemAvailableMinKiB >= 300 * 1024
    && summary.diskAvailableMinKiB > 0 && summary.maxContinuousPagingSeconds < 60 && summary.oomEvents === 0
    && summary.recoveryEndWorkingSetBytes <= summary.workingSetMaxBytes
  return summary
}

async function sampleContainer(id) {
  const output = await execFileAsync('docker', ['exec', id, 'sh', '-c', "printf 'memory.current=';cat /sys/fs/cgroup/memory.current; printf 'memory.peak=';cat /sys/fs/cgroup/memory.peak; printf 'pids.current=';cat /sys/fs/cgroup/pids.current; printf 'pids.peak=';cat /sys/fs/cgroup/pids.peak; sed -n 's/^file /file=/p;s/^inactive_file /inactive_file=/p' /sys/fs/cgroup/memory.stat; sed 's/ /=/' /sys/fs/cgroup/memory.events; sed 's/ /=/' /sys/fs/cgroup/cpu.stat; tr '\n' ';' </sys/fs/cgroup/io.stat"], { encoding: 'utf8', timeout: 2500, maxBuffer: 1024 * 1024 })
  return Object.fromEntries(output.stdout.trim().split(/\r?\n/).filter(Boolean).map(line => { const at = line.indexOf('='); return [line.slice(0, at), line.slice(at + 1)] }))
}

async function sampleHost(dbId) {
  const output = await execFileAsync('docker', ['exec', dbId, 'sh', '-c', "awk '/^(MemAvailable|SwapTotal|SwapFree):/{print $1 \"=\" $2}' /proc/meminfo; awk '/^(pswpin|pswpout) /{print $1 \"=\" $2}' /proc/vmstat; df -Pk /var/lib/postgresql/data | tail -1 | awk '{print \"diskAvailableKiB=\"$4 \"\\ndiskUsedPercent=\"$5}'"], { encoding: 'utf8', timeout: 2500 })
  return Object.fromEntries(output.stdout.trim().split(/\r?\n/).map(line => line.split('=')))
}

async function sampleDatabase(dbId) {
  const sql = "SELECT json_build_object('connections',count(*),'active',count(*) FILTER(WHERE state='active'),'activeWaiting',count(*) FILTER(WHERE state='active' AND wait_event IS NOT NULL),'longTransactions',count(*) FILTER(WHERE xact_start<now()-interval '5 seconds')) FROM pg_stat_activity WHERE datname=current_database(); SELECT json_build_object('outboxPending',count(*) FILTER(WHERE status IN('PENDING','PROCESSING')),'outboxFailed',count(*) FILTER(WHERE status='FAILED')) FROM outbox_event;"
  const output = await execFileAsync('docker', ['exec', dbId, 'sh', '-c', `psql -X -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c ${JSON.stringify(sql)}`], { encoding: 'utf8', timeout: 2500 })
  return output.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line))
}

function windowFor(now, start, warmMs, normalMs) {
  if (now < start + warmMs) return 'warmup'
  if (now < start + warmMs + normalMs) return 'normal'
  return 'recovery'
}

function cookieClient() {
  const jar = new Map()
  return async (path, { method = 'GET', body, headers = {} } = {}) => {
    const response = await fetch(new URL(path, baseUrl), {
      method,
      headers: { ...headers, ...(jar.size ? { cookie: [...jar].map(([key, value]) => `${key}=${value}`).join('; ') } : {}), ...(body ? { 'content-type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(8000),
    })
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';'), at = pair.indexOf('=')
      if (at > 0) jar.set(pair.slice(0, at), pair.slice(at + 1))
    }
    const text = await response.text()
    if (!response.ok) throw new Error(`${method} ${path}: ${response.status}`)
    return text ? JSON.parse(text) : null
  }
}

async function adminClient() {
  const username = process.env.HAOBLOG_S6_ADMIN_USERNAME
  const password = process.env.HAOBLOG_S6_ADMIN_PASSWORD
  assert.ok(username && password, 'business write windows require HAOBLOG_S6_ADMIN_USERNAME and HAOBLOG_S6_ADMIN_PASSWORD')
  const request = cookieClient()
  const anonymous = await request('/api/v1/admin/csrf')
  await request('/api/v1/admin/session', { method: 'POST', headers: { 'X-CSRF-TOKEN': anonymous.token }, body: { username, password } })
  const authenticated = await request('/api/v1/admin/csrf')
  return (path, options = {}) => request(path, { ...options, headers: { ...options.headers, 'X-CSRF-TOKEN': authenticated.token } })
}

async function waitPublic(slug, marker, deadline = Date.now() + 60_000) {
  while (Date.now() < deadline) {
    const [article, rss, sitemap] = await Promise.all([
      fetch(new URL(`/api/v1/public/articles/${slug}`, baseUrl), { signal: AbortSignal.timeout(8000) }),
      fetch(new URL('/rss.xml', baseUrl), { signal: AbortSignal.timeout(8000) }),
      fetch(new URL('/sitemap.xml', baseUrl), { signal: AbortSignal.timeout(8000) }),
    ])
    const [articleText, rssText, sitemapText] = await Promise.all([article.text(), rss.text(), sitemap.text()])
    if (article.status === 200 && articleText.includes(marker) && rssText.includes(slug) && sitemapText.includes(slug)) return Date.now()
    await new Promise(resolveWait => setTimeout(resolveWait, 2000))
  }
  throw new Error(`${slug}: public/API/RSS/Sitemap did not converge within 60 seconds`)
}

async function submitComment(slug, suffix) {
  const request = cookieClient()
  const form = await request(`/api/v1/public/articles/${slug}/comments/form-context`)
  await new Promise(resolveWait => setTimeout(resolveWait, 3200))
  return request(`/api/v1/public/articles/${slug}/comments`, { method: 'POST', headers: { 'X-CSRF-TOKEN': form.csrfToken }, body: { nickname: `S6访客${suffix}`, content: `S6 运行窗口评论 ${suffix}`, challenge: form.challenge } })
}

async function businessEvent(kind) {
  const began = Date.now(), admin = await adminClient(), suffix = `${runId}-${kind}`
  if (kind === 'publish') {
    const slug = `s6-live-${runId.replaceAll('-', '')}`
    const publicMarker = `PUBLIC-${suffix}`, secretMarker = `WORKCOPY-${suffix}`
    const created = await admin('/api/v1/admin/articles', { method: 'POST', body: { slug, title: `阶段六运行发布 ${suffix}`, excerpt: publicMarker, markdown: `# ${publicMarker}\n\n公开版本。`, commentsEnabled: true } })
    const published = await admin(`/api/v1/admin/articles/${created.id}/publish`, { method: 'POST', body: { version: created.version } })
    await admin(`/api/v1/admin/articles/${created.id}`, { method: 'PUT', body: { version: published.version, slug, title: `阶段六运行发布 ${suffix}`, excerpt: secretMarker, markdown: `# ${secretMarker}\n\n尚未发布的工作副本。`, commentsEnabled: true, tagIds: [] } })
    const visibleAt = await waitPublic(slug, publicMarker)
    const publicBody = await (await fetch(new URL(`/api/v1/public/articles/${slug}`, baseUrl), { signal: AbortSignal.timeout(8000) })).text()
    assert.ok(!publicBody.includes(secretMarker), 'working copy leaked into the public API')
    await submitComment(slug, kind)
    return { kind, slug, durationMs: Date.now() - began, visibilityMs: visibleAt - began, workCopyHidden: true }
  }
  const slug = `s6-due-${runId.replaceAll('-', '')}`
  const marker = `SCHEDULED-${suffix}`, scheduledAt = new Date(Date.now() + 10_000).toISOString()
  const created = await admin('/api/v1/admin/articles', { method: 'POST', body: { slug, title: `阶段六到期计划 ${suffix}`, excerpt: marker, markdown: `# ${marker}\n\n到期计划。`, commentsEnabled: true } })
  await admin(`/api/v1/admin/articles/${created.id}/schedule`, { method: 'POST', body: { version: created.version, scheduledAt } })
  const visibleAt = await waitPublic(slug, marker)
  await submitComment(slug, kind)
  return { kind, slug, scheduledAt, durationMs: Date.now() - began, visibilityMs: visibleAt - began }
}

async function backupEvent() {
  const began = Date.now(), dbId = container('postgres-pgvector')
  const tempRoot = resolve(process.env.HAOBLOG_S6_TEMP_DIR || resolve(process.env.TEMP || '.', runId))
  const docsRoot = resolve('docs')
  assert.ok(tempRoot !== docsRoot && !tempRoot.startsWith(docsRoot + sep), 'pg_dump must not be retained under docs')
  assert.ok(tempRoot.endsWith(`${sep}${runId}`), 'pg_dump temp directory must be task-specific')
  mkdirSync(tempRoot, { recursive: true })
  const dumpPath = resolve(tempRoot, `${runId}.dump`)
  try {
    const resourceBefore = await sampleContainer(dbId)
    const dump = await execFileAsync('docker', ['exec', dbId, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], { encoding: null, timeout: 120_000, maxBuffer: 128 * 1024 ** 2 })
    const durationMs = Date.now() - began
    writeFileSync(dumpPath, dump.stdout)
    const listing = run('docker', ['exec', '-i', dbId, 'pg_restore', '--list'], { input: dump.stdout })
    return { kind: 'pg_dump', exitCode: 0, durationMs, sizeBytes: statSync(dumpPath).size, sha256: createHash('sha256').update(dump.stdout).digest('hex'), listedEntries: listing.split(/\r?\n/).filter(line => line && !line.startsWith(';')).length, resourceBefore, resourceAfter: await sampleContainer(dbId) }
  } finally {
    rmSync(dumpPath, { force: true })
  }
}

async function loadRun() {
  validateConfig({ destructive: true })
  const admission = await preflight()
  const target = prepareEvidence()
  const marker = `S6 acceptance ${runId}`
  assert.equal(admission.site.description, marker, 'fixed synthetic dataset marker is missing')
  const timings = mode === 'formal' ? { warm: 300, normal: 1800, recovery: 300, events: [600, 900, 1200] } : { warm: 60, normal: 300, recovery: 60, events: [120, 180, 230] }
  const override = process.env.HAOBLOG_S6_TIMINGS
  if (override) {
    assert.notEqual(mode, 'formal', 'formal timings are immutable')
    const parsed = override.split(',').map(Number)
    assert.ok(parsed.length === 3 && parsed.every(value => Number.isInteger(value) && value >= 5), 'HAOBLOG_S6_TIMINGS must be warm,normal,recovery seconds')
    timings.warm = parsed[0]; timings.normal = parsed[1]; timings.recovery = parsed[2]
    timings.events = [Math.floor(parsed[1] * .4), Math.floor(parsed[1] * .6), Math.floor(parsed[1] * .8)]
  }
  const warmMs = timings.warm * 1000, normalMs = timings.normal * 1000, recoveryMs = timings.recovery * 1000
  const start = Date.now(), loadEnd = start + warmMs + normalMs, end = loadEnd + recoveryMs
  const requests = [], metrics = [], gaps = [], events = [], etags = new Map(), controller = new AbortController()
  const requestStream = createWriteStream(resolve(target, 'requests.jsonl'))
  const metricsStream = createWriteStream(resolve(target, 'metrics.jsonl'))
  let stopped = false
  const stop = () => { stopped = true; controller.abort(new Error('acceptance interrupted')) }
  process.once('SIGINT', stop); process.once('SIGTERM', stop)
  const recordRequest = record => { requests.push(record); requestStream.write(`${JSON.stringify(record)}\n`) }
  async function timed(category, path, headers = {}) {
    const began = performance.now(); let status = 0, outcome = 'failure', responseBytes = 0
    try {
      const response = await fetch(new URL(path, baseUrl), { headers, signal: AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]) })
      status = response.status; responseBytes = (await response.arrayBuffer()).byteLength
      outcome = status === 200 || status === 304 ? 'success' : 'failure'
      return response
    } catch (error) {
      outcome = controller.signal.aborted ? 'aborted' : error?.name === 'TimeoutError' ? 'timeout' : 'failure'
      return null
    } finally {
      recordRequest({ at: new Date().toISOString(), window: windowFor(Date.now(), start, warmMs, normalMs), category, status, outcome, responseBytes, durationMs: Math.round((performance.now() - began) * 10) / 10 })
    }
  }
  const slugs = ['s3-08-advanced-markdown', 's6-normal-chinese', 's6-near-one-mib', ...Array.from({ length: 17 }, (_, index) => `s6-article-${String(index + 4).padStart(3, '0')}`)]
  const htmlWorkers = Array.from({ length: 20 }, (_, worker) => (async () => {
    let index = worker
    while (!stopped && Date.now() < loadEnd) {
      const slug = slugs[index++ % slugs.length], saveData = index % 5 === 0
      const cacheKey = `${slug}:${saveData}`, conditional = index % 4 === 0 && etags.has(cacheKey)
      const response = await timed('article-html', `/articles/${slug}`, { ...(saveData ? { 'Save-Data': 'on' } : {}), ...(conditional ? { 'If-None-Match': etags.get(cacheKey) } : {}) })
      if (response?.status === 200 && response.headers.get('etag')) etags.set(cacheKey, response.headers.get('etag'))
    }
  })())
  const apiPaths = [
    ['site', '/api/v1/public/site'], ['article-list', '/api/v1/public/articles?page=0&size=20'], ['search', '/api/v1/public/search/articles?q=中文&page=0&size=20'],
    ['tools', '/api/v1/public/tools'], ['garden', '/api/v1/public/garden'], ['comments', '/api/v1/public/articles/s3-08-advanced-markdown/comments'], ['rss', '/rss.xml'], ['sitemap', '/sitemap.xml'],
  ]
  const apiWorkers = Array.from({ length: 2 }, (_, worker) => (async () => {
    let index = worker
    while (!stopped && Date.now() < loadEnd) {
      const tick = Date.now(); const [category, path] = apiPaths[index++ % apiPaths.length]
      await timed(category, path, index % 3 ? {} : { 'Save-Data': 'on' })
      await new Promise(resolveWait => setTimeout(resolveWait, Math.max(0, 1000 - (Date.now() - tick))))
    }
  })())
  const ids = Object.fromEntries(services.map(service => [service, admission.projectState.services[service].id]))
  const eventRunner = (async () => {
    const jobs = [['publish', timings.events[0]], ['schedule', timings.events[1]], ['pg_dump', timings.events[2]]]
    for (const [kind, seconds] of jobs) {
      await new Promise(resolveWait => setTimeout(resolveWait, Math.max(0, start + warmMs + seconds * 1000 - Date.now())))
      if (stopped) return
      try {
        const eventStart = Date.now()
        const result = kind === 'pg_dump' ? await backupEvent() : await businessEvent(kind)
        const eventEnd = Date.now()
        events.push({ ...result, startedAt: new Date(eventStart).toISOString(), endedAt: new Date(eventEnd).toISOString(), startOffsetMs: eventStart - start, endOffsetMs: eventEnd - start })
      }
      catch (error) { events.push({ kind, error: String(error.message || error) }); stopped = true; controller.abort(error) }
    }
  })()
  const sampler = (async () => {
    let next = start, count = 0
    while (!stopped && Date.now() < end) {
      const began = Date.now()
      try {
        const [containers, host, database] = await Promise.all([
          Promise.all(services.map(async service => [service, await sampleContainer(ids[service])])), sampleHost(ids['postgres-pgvector']), count % 5 === 0 ? sampleDatabase(ids['postgres-pgvector']) : null,
        ])
        const sample = { at: new Date().toISOString(), offsetMs: began - start, containers: Object.fromEntries(containers), host, database }
        metrics.push(sample); metricsStream.write(`${JSON.stringify(sample)}\n`)
      } catch (error) { gaps.push({ at: new Date().toISOString(), error: String(error.message || error) }) }
      count += 1; next += 1000
      await new Promise(resolveWait => setTimeout(resolveWait, Math.max(0, next - Date.now())))
    }
  })()
  const progress = setInterval(() => console.log(JSON.stringify({ event: 'progress', elapsedSeconds: Math.floor((Date.now() - start) / 1000), window: windowFor(Date.now(), start, warmMs, normalMs), requests: requests.length })), 60_000)
  try {
    await Promise.all([...htmlWorkers, ...apiWorkers, eventRunner])
    if (!stopped) await new Promise(resolveWait => setTimeout(resolveWait, Math.max(0, end - Date.now())))
    await sampler
  } finally {
    clearInterval(progress); controller.abort()
    await Promise.all([new Promise(resolveEnd => requestStream.end(resolveEnd)), new Promise(resolveEnd => metricsStream.end(resolveEnd))])
  }
  const restarts = Object.fromEntries(services.map(service => [service, Number(docker('inspect', '--format', '{{.RestartCount}}', ids[service]))]))
  const inEvent = (request, event) => request.at >= event.startedAt && request.at <= event.endedAt
  const eventSummaries = Object.fromEntries(events.map(event => [event.kind, summarize(requests.filter(request => inEvent(request, event)))]))
  const steadySummary = summarize(requests.filter(request => request.window === 'normal' && !events.some(event => inEvent(request, event))))
  const normal = requests.filter(request => request.window === 'normal')
  const htmlP95 = percentile(normal.filter(request => request.category === 'article-html' && request.status === 200).map(request => request.durationMs), .95)
  const apiP95 = Object.fromEntries(apiPaths.map(([category]) => [category, percentile(normal.filter(request => request.category === category && request.status === 200).map(request => request.durationMs), .95)]))
  const successRate = normal.filter(request => request.outcome === 'success').length / normal.length
  const resources = summarizeResources(metrics, warmMs, normalMs, timings.warm + timings.normal + timings.recovery)
  const acceptance = { successRate, htmlP95, apiP95, resources, passed: successRate >= .999 && htmlP95 !== null && htmlP95 < 500 && Object.values(apiP95).every(value => value !== null && value < 300) && resources.passed && !stopped && !gaps.length && Object.values(restarts).every(value => value === 0) }
  const report = { runId, mode, admission, timings, loadModel: { htmlWorkers: 20, apiWorkers: 2, apiMaxRpsEach: 1, retries: 0 }, startedAt: new Date(start).toISOString(), endedAt: new Date().toISOString(), interrupted: stopped, requestCount: requests.length, summary: summarize(requests), steadySummary, eventSummaries, events, samplingGaps: gaps, restarts, acceptance }
  writeFileSync(resolve(target, 'load-report.json'), JSON.stringify(report, null, 2))
  if (!acceptance.passed) process.exitCode = 1
  return report
}

function growthSql() {
  const lines = ['BEGIN;']
  for (let index = 1; index <= 2000; index += 1) {
    const articleId = uuid(8, index), revisionId = uuid(9, index), slug = `s6-growth-${String(index).padStart(4, '0')}`
    const title = `阶段六增长样本 ${index}`, markdown = `# ${title}\n\n图谱聚合随机冷渲染样本 ${index}。`
    lines.push(`INSERT INTO article(id,slug,title,excerpt,markdown_source,status,published_at,created_at,updated_at,version,category_id,comments_enabled) VALUES('${articleId}','${slug}','${title}','增长样本','${markdown}','PUBLISHED',now()-interval '${index} seconds',now(),now(),0,'${uuid(1, (index - 1) % 25 + 1)}',true) ON CONFLICT(slug) DO NOTHING;`)
    lines.push(`INSERT INTO article_revision(id,article_id,source_version,title,slug,excerpt,markdown_source,category_snapshot,tag_snapshot,change_reason,created_at) VALUES('${revisionId}','${articleId}',0,'${title}','${slug}','增长样本','${markdown}',NULL,'[]','S6 growth fixture',now()) ON CONFLICT DO NOTHING;`)
    lines.push(`UPDATE article SET published_revision_id='${revisionId}' WHERE id='${articleId}' AND published_revision_id IS NULL;`)
  }
  lines.push('COMMIT;')
  return lines.join('\n')
}

async function growth() {
  validateConfig({ destructive: true }); await preflight(); const target = prepareEvidence()
  psql(growthSql())
  const count = Number(query("SELECT count(*) FROM article WHERE slug LIKE 's6-growth-%';"))
  assert.equal(count, 2000)
  let state = 0x6a09e667, failures = 0; const observations = [], sampled = new Set()
  for (let sample = 0; sample < 100; sample += 1) {
    let index
    do { state = (Math.imul(state, 1664525) + 1013904223) >>> 0; index = state % 2000 + 1 } while (sampled.has(index))
    sampled.add(index)
    const path = `/articles/s6-growth-${String(index).padStart(4, '0')}`, began = performance.now()
    const response = await fetch(new URL(path, baseUrl), { signal: AbortSignal.timeout(8000) })
    if (response.status !== 200) failures += 1
    await response.arrayBuffer(); observations.push({ sample, index, status: response.status, durationMs: Math.round((performance.now() - began) * 10) / 10 })
  }
  assert.equal(sampled.size, 100)
  const graph = await fetch(new URL('/api/v1/public/garden', baseUrl), { signal: AbortSignal.timeout(8000) }); const graphBody = await graph.json()
  const report = { runId, count, failures, randomSeed: '0x6a09e667', p95: percentile(observations.map(item => item.durationMs), .95), graphStatus: graph.status, graph: { nodes: graphBody.nodes?.length, edges: graphBody.edges?.length, truncated: graphBody.truncated }, observations }
  writeFileSync(resolve(target, 'growth-report.json'), JSON.stringify(report, null, 2))
  if (failures || graph.status !== 200 || graphBody.truncated !== true) process.exitCode = 1
  return report
}

async function selfTest() {
  assert.equal(uuid(7, 1)[14], '7')
  assert.deepEqual([percentile([30, 10, 20], .5), percentile([30, 10, 20], .95)], [20, 30])
  const records = [{ window: 'normal', category: 'x', status: 200, outcome: 'success', durationMs: 10 }, { window: 'normal', category: 'x', status: 0, outcome: 'timeout', durationMs: 8000 }, { window: 'normal', category: 'x', status: 503, outcome: 'failure', durationMs: 2 }]
  const summary = summarize(records)
  assert.equal(summary['normal:x:other'].timeout, 1)
  assert.equal(summary['normal:x:other'].rejected, 1)
  assert.equal(summary['normal:x:other'].total, 2)
  const resource = offsetMs => ({ offsetMs, containers: { api: { 'memory.current': '1000', inactive_file: '100', oom: '0', oom_kill: '0' } }, host: { 'MemAvailable:': '400000', diskAvailableKiB: '1', pswpin: '0', pswpout: '0' }, database: [{}, {}] })
  assert.equal(summarizeResources([resource(0), resource(1000), resource(2000)], 0, 2000, 3).passed, true)
  assert.equal(summarizeResources([resource(0), resource(1000)], 0, 2000, 5).passed, false)
  const oversized = resource(0); oversized.containers.api['memory.current'] = String(1.6 * 1024 ** 3)
  assert.equal(summarizeResources([oversized], 0, 1000, 1).passed, false)
  assert.throws(() => new URL('not-an-origin'))
  assert.match(advancedMarkdownFixture(), /正文、公式和代码在 SSR HTML 中直接可读。/)
  assert.equal(advancedMarkdownFixture().match(/::: (?:note|tip|warning|danger)/g)?.length, 4)
  console.log('stage6 acceptance self-test passed')
}

if (action === 'self-test') await selfTest()
else if (action === 'preflight') console.log(JSON.stringify(await preflight(), null, 2))
else if (action === 'seed') console.log(JSON.stringify(await seed(), null, 2))
else if (action === 'run') console.log(JSON.stringify(await loadRun(), null, 2))
else if (action === 'growth') console.log(JSON.stringify(await growth(), null, 2))
else throw new Error('usage: stage6-acceptance.mjs self-test|preflight [preflight|formal]|seed [preflight|formal]|run [preflight|formal]|growth [preflight|formal]')
