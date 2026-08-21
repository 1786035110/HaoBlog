import { readdir, readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const clientDir = fileURLToPath(new URL('../.output/public/_nuxt', import.meta.url))
const forbidden = /(?:shiki|@shikijs|oniguruma|vscode-textmate)/i

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
  const matches = []
  for (const file of files) {
    if (forbidden.test(await readFile(file, 'utf8'))) matches.push(file)
  }
  if (matches.length) {
    throw new Error(`文章客户端初始资源包含服务端 Shiki 依赖：${matches.join(', ')}`)
  }
  console.log(`文章客户端包检查通过：已扫描 ${files.length} 个浏览器 JS chunk，未发现 Shiki/正则引擎依赖。`)
} catch (error) {
  if (error?.code === 'ENOENT') throw new Error('未找到构建产物，请先运行 pnpm build。')
  throw error
}
