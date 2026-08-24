import { readdir, readFile } from 'node:fs/promises'
import { basename, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const clientDir = fileURLToPath(new URL('../.output/public/_nuxt', import.meta.url))
const ARTICLE_WARNING_GZIP_BYTES = 80 * 1024
const ARTICLE_MAX_GZIP_BYTES = 180 * 1024
const THREE_MAX_GZIP_BYTES = 500 * 1024

const routeMarkers = {
  article: 'ArticlesPublicArticleBody',
  home: 'FIRST FRAME / 夜空校准',
  garden: 'DIGITAL GARDEN / KNOWLEDGE SIGNAL',
}

const heavyBodies = {
  Three: /(?:THREE \/ INSTANCED SIGNAL FIELD|WebGLRenderer|InstancedMesh)/,
  'd3-force': /forceSimulation[\s\S]{0,800}alphaMin[\s\S]{0,800}velocityDecay/,
  播放器: /SIGNAL TAPE \/ MUSIC|频谱待用户播放后接入/,
  终端: /安全信号终端|命令不能超过 200 个字符/,
}

const articleForbidden = {
  ...heavyBodies,
  '服务端 Markdown 重依赖': /(?:shiki|@shikijs|oniguruma|vscode-textmate|katex(?:\.render|\.js|\/dist)|mermaid(?:\.core|\/dist)|@mermaid-js|monaco|codemirror|prosemirror)/i,
}

async function collectFiles(directory) {
  const files = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) files.push(...await collectFiles(path))
    else if (/\.(?:js|mjs|cjs)$/.test(entry.name)) files.push(path)
  }
  return files
}

function staticImports(source) {
  return [...source.matchAll(/(?:from|import)"\.\/([^" ]+\.js)"/g)].map(match => match[1])
}

function staticClosure(entry, byName, sources) {
  const closure = new Set()
  const queue = [entry]
  while (queue.length) {
    const file = queue.pop()
    if (!file || closure.has(file)) continue
    closure.add(file)
    for (const name of staticImports(sources.get(file) || '')) {
      const dependency = byName.get(name)
      if (dependency) queue.push(dependency)
    }
  }
  return closure
}

function gzipBytes(files, sources) {
  return [...files].reduce((total, file) => total + gzipSync(sources.get(file) || '').byteLength, 0)
}

function matchingFiles(files, sources, pattern) {
  return [...files].filter(file => pattern.test(sources.get(file) || ''))
}

function requireRouteEntry(files, sources, route) {
  const entry = files.find(file => (sources.get(file) || '').includes(routeMarkers[route]))
  if (!entry) throw new Error(`未找到 ${route} 路由客户端入口，无法检查初始包。`)
  return entry
}

function rejectMatches(label, files, sources, patterns) {
  const matches = []
  for (const [name, pattern] of Object.entries(patterns)) {
    for (const file of matchingFiles(files, sources, pattern)) matches.push(`${name}:${basename(file)}`)
  }
  if (matches.length) throw new Error(`${label}初始静态闭包含有重型主体：${matches.join(', ')}`)
}

try {
  const files = await collectFiles(clientDir)
  const sources = new Map(await Promise.all(files.map(async file => [file, await readFile(file, 'utf8')])))
  const byName = new Map(files.map(file => [basename(file), file]))

  const articleEntry = requireRouteEntry(files, sources, 'article')
  const articleInitial = staticClosure(articleEntry, byName, sources)
  const homeInitial = staticClosure(requireRouteEntry(files, sources, 'home'), byName, sources)
  const gardenInitial = staticClosure(requireRouteEntry(files, sources, 'garden'), byName, sources)

  rejectMatches('文章', articleInitial, sources, articleForbidden)
  rejectMatches('首页', homeInitial, sources, { Three: heavyBodies.Three })
  rejectMatches('花园', gardenInitial, sources, { 'd3-force': heavyBodies['d3-force'] })

  const articleGzipBytes = gzipBytes(articleInitial, sources)
  if (articleGzipBytes > ARTICLE_MAX_GZIP_BYTES) {
    throw new Error(`文章客户端初始 JS 超出 180 KiB gzip 硬上限：${articleGzipBytes} bytes`)
  }
  if (articleGzipBytes > ARTICLE_WARNING_GZIP_BYTES) {
    console.warn(`文章客户端初始 JS 超过阶段五 80 KiB gzip 回归警戒线：${articleGzipBytes} bytes`)
  }

  const threeChunks = matchingFiles(files, sources, heavyBodies.Three)
  if (!threeChunks.length) throw new Error('未找到 Three 动态 chunk。')
  const initialRoutes = new Set([...articleInitial, ...homeInitial, ...gardenInitial])
  const initialThree = threeChunks.filter(file => initialRoutes.has(file))
  if (initialThree.length) throw new Error(`Three 进入了路由初始静态闭包：${initialThree.map(basename).join(', ')}`)
  for (const file of threeChunks) {
    const compressed = gzipSync(sources.get(file) || '').byteLength
    if (compressed > THREE_MAX_GZIP_BYTES) throw new Error(`Three 动态 chunk ${basename(file)} 超出 500 KiB gzip：${compressed} bytes`)
  }

  const articleSource = sources.get(articleEntry) || ''
  const mermaidImport = articleSource.match(/(?:startOnLoad|maxTextSize|haoblog-mermaid-)[\s\S]{0,800}?import\(`\.\/([^`]+\.js)`\)/)
  if (!mermaidImport) throw new Error('未发现 Mermaid 动态 import。')
  const mermaidChunk = byName.get(mermaidImport[1])
  if (!mermaidChunk || articleInitial.has(mermaidChunk)) throw new Error('Mermaid 运行时进入了文章客户端初始静态依赖。')
  if (!/mermaid|securityLevel/i.test(sources.get(mermaidChunk) || '')) throw new Error('Mermaid 动态 chunk 内容异常。')

  const threeReport = threeChunks.map(file => `${basename(file)} ${gzipSync(sources.get(file) || '').byteLength} bytes gzip`).join('，')
  console.log(`客户端预算通过：文章静态闭包 ${articleInitial.size} 个 chunk，${articleGzipBytes} bytes gzip（80 KiB 警戒线 / 180 KiB 硬上限）；首页 Three 与花园 d3-force 均为动态加载；Three 动态 chunk：${threeReport}；Mermaid 位于异步 chunk ${mermaidImport[1]}。`)
} catch (error) {
  if (error?.code === 'ENOENT') throw new Error('未找到构建产物，请先运行 pnpm build。')
  throw error
}
