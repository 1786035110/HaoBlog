import { readdir, readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const clientDir = fileURLToPath(new URL('../.output/public/_nuxt', import.meta.url))
const MAX_INITIAL_GZIP_BYTES = 180 * 1024
const forbidden = /(?:shiki|@shikijs|oniguruma|vscode-textmate|katex(?:\.render|\.js|\/dist)|mermaid(?:\.core|\/dist)|@mermaid-js|(?:THREE\.|three\/build)|monaco|codemirror|prosemirror)/i

async function collectFiles(directory) {
  const files = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) files.push(...await collectFiles(path))
    else if (/\.(?:js|mjs|cjs)$/.test(entry.name)) files.push(path)
  }
  return files
}

try {
  const files = await collectFiles(clientDir)
  const sources = new Map(await Promise.all(files.map(async file => [file, await readFile(file, 'utf8')])))
  const byName = new Map(files.map(file => [file.split(/[\\/]/).pop(), file]))
  const articleEntry = files.find(file => sources.get(file)?.includes('ArticlesPublicArticleBody'))
  if (!articleEntry) throw new Error('未找到文章路由客户端入口，无法检查初始包。')

  const staticImports = source => [...source.matchAll(/(?:from|import)"\.\/([^" ]+\.js)"/g)].map(match => match[1])
  const initial = new Set()
  const queue = [articleEntry]
  while (queue.length) {
    const file = queue.pop()
    if (!file || initial.has(file)) continue
    initial.add(file)
    for (const name of staticImports(sources.get(file) || '')) {
      const dependency = byName.get(name)
      if (dependency) queue.push(dependency)
    }
  }

  const matches = [...initial].filter(file => forbidden.test(sources.get(file) || ''))
  if (matches.length) throw new Error(`文章客户端初始资源包含服务端 Shiki/KaTeX 依赖：${matches.join(', ')}`)

  const initialGzipBytes = [...initial].reduce((total, file) => total + gzipSync(sources.get(file) || '').byteLength, 0)
  if (initialGzipBytes > MAX_INITIAL_GZIP_BYTES) {
    throw new Error(`文章客户端初始 JS 超出 180KB gzip 预算：${initialGzipBytes} bytes`)
  }

  const articleSource = sources.get(articleEntry) || ''
  const mermaidImport = articleSource.match(/(?:startOnLoad|maxTextSize|haoblog-mermaid-)[\s\S]{0,800}?import\(`\.\/([^`]+\.js)`\)/)
  if (!mermaidImport) throw new Error('未发现 Mermaid 动态 import。')
  const mermaidChunk = byName.get(mermaidImport[1])
  if (!mermaidChunk || initial.has(mermaidChunk)) throw new Error('Mermaid 运行时进入了文章客户端初始静态依赖。')
  if (!/mermaid|securityLevel/i.test(sources.get(mermaidChunk) || '')) throw new Error('Mermaid 动态 chunk 内容异常。')

  console.log(`文章客户端预算通过：静态闭包 ${initial.size} 个 chunk，${initialGzipBytes} bytes gzip；Mermaid 位于异步 chunk ${mermaidImport[1]}。`)
} catch (error) {
  if (error?.code === 'ENOENT') throw new Error('未找到构建产物，请先运行 pnpm build。')
  throw error
}
