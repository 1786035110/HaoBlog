import { describe, expect, it } from 'vitest'
import { parseTerminalCommand } from '../app/utils/terminalCommands'

describe('安全终端白名单解析器', () => {
  it('接受本阶段全部合法命令', () => {
    expect(parseTerminalCommand('help')).toEqual({ kind: 'help' })
    expect(parseTerminalCommand('ls posts')).toEqual({ kind: 'list', target: 'posts' })
    expect(parseTerminalCommand('ls tools')).toEqual({ kind: 'list', target: 'tools' })
    expect(parseTerminalCommand('grep "Spring AI"')).toEqual({ kind: 'grep', query: 'Spring AI' })
    expect(parseTerminalCommand('open /articles/spring-ai')).toEqual({ kind: 'open', href: '/articles/spring-ai' })
    expect(parseTerminalCommand('open /posts/spring-ai')).toEqual({ kind: 'open', href: '/articles/spring-ai' })
    expect(parseTerminalCommand('theme night')).toEqual({ kind: 'theme', theme: 'night' })
    expect(parseTerminalCommand('theme blueprint')).toEqual({ kind: 'theme', theme: 'blueprint' })
    expect(parseTerminalCommand('clear')).toEqual({ kind: 'clear' })
    expect(parseTerminalCommand('ask "如何实现 RAG"')).toEqual({ kind: 'ask', question: '如何实现 RAG' })
  })

  it('拒绝 Shell、脚本、外部 URL、路径穿越和越界输入', () => {
    const rejected = [
      'ls posts; alert(1)',
      'grep "x" | cat',
      'open https://example.com',
      'open /articles/../secret',
      'open /articles/one/two',
      'ask question',
      `grep "${'x'.repeat(101)}"`,
      'rm -rf /',
      '<script>alert(1)</script>',
    ]
    rejected.forEach(input => expect(parseTerminalCommand(input).kind, input).toBe('error'))
  })
})
