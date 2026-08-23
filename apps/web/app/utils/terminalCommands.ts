export type TerminalCommand =
  | { kind: 'help' }
  | { kind: 'list'; target: 'posts' | 'tools' }
  | { kind: 'grep'; query: string }
  | { kind: 'open'; href: string }
  | { kind: 'theme'; theme: 'night' | 'blueprint' }
  | { kind: 'clear' }
  | { kind: 'ask'; question: string }

const MAX_COMMAND_LENGTH = 200
const MAX_ARGUMENT_LENGTH = 100
const SAFE_SLUG = '[a-z0-9](?:[a-z0-9-]{0,118}[a-z0-9])?'
const FORBIDDEN_INPUT = /[;|&<>`$]|\b(?:javascript|data|file|http|https):|\/\//i

export function parseTerminalCommand(input: string): TerminalCommand | { kind: 'error'; message: string } {
  const command = input.trim()
  if (!command) return { kind: 'error', message: '请输入命令。可输入 help 查看白名单。' }
  if (Array.from(command).length > MAX_COMMAND_LENGTH) return { kind: 'error', message: `命令不能超过 ${MAX_COMMAND_LENGTH} 个字符。` }
  if (FORBIDDEN_INPUT.test(command)) return { kind: 'error', message: '已拒绝：终端不执行脚本、管道、重定向或外部 URL。' }

  if (command === 'help') return { kind: 'help' }
  if (command === 'clear') return { kind: 'clear' }
  if (command === 'ls posts') return { kind: 'list', target: 'posts' }
  if (command === 'ls tools') return { kind: 'list', target: 'tools' }
  if (command === 'theme night') return { kind: 'theme', theme: 'night' }
  if (command === 'theme blueprint') return { kind: 'theme', theme: 'blueprint' }

  const quoted = command.match(/^(grep|ask) "([^"]*)"$/)
  if (quoted) {
    const value = quoted[2]
    if (!value || Array.from(value).length > MAX_ARGUMENT_LENGTH) {
      return { kind: 'error', message: `参数不能为空且不能超过 ${MAX_ARGUMENT_LENGTH} 个字符。` }
    }
    return quoted[1] === 'grep' ? { kind: 'grep', query: value } : { kind: 'ask', question: value }
  }

  const open = command.match(new RegExp(`^open (\\/(?:articles|posts)\\/${SAFE_SLUG})$`))
  if (open) return { kind: 'open', href: open[1]!.replace(/^\/posts\//, '/articles/') }
  if (/^open\s/.test(command)) return { kind: 'error', message: '只允许打开 /articles/{slug} 或兼容别名 /posts/{slug}。' }
  if (/^(grep|ask)\s/.test(command)) return { kind: 'error', message: '只接受双引号包裹的有限长度参数。' }
  if (/^(ls|theme)\s/.test(command)) return { kind: 'error', message: '该命令的参数不在白名单中。' }
  return { kind: 'error', message: '未知命令。可输入 help 查看白名单。' }
}

export const terminalHelp = [
  'help                 显示白名单命令',
  'ls posts             列出最近公开文章（最多 20 条）',
  'ls tools             列出公开工具（最多 20 条）',
  'grep "query"         搜索公开文章（最多 20 条）',
  'open /articles/slug  打开文章；/posts/slug 为兼容别名',
  'theme night|blueprint 切换本地主题',
  'clear                清空当前输出',
  'ask "question"       显示 AI 未开放提示和站内搜索入口',
]
