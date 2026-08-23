import { onBeforeUnmount, type InjectionKey } from 'vue'
import { type EmbeddedWorkerPayload, type EmbeddedWorkerResult, type EmbeddedWorkerTask } from '../utils/embeddedToolOperations'

type WorkerResponse = { id: number; ok: boolean; result?: EmbeddedWorkerResult; error?: string }
type Pending = { resolve: (result: EmbeddedWorkerResult) => void; reject: (error: Error) => void; timer: ReturnType<typeof setTimeout> }

export const embeddedWorkerKey: InjectionKey<EmbeddedWorkerClient> = Symbol('haoblog-embedded-worker')

export class EmbeddedWorkerClient {
  private worker?: Worker
  private nextId = 0
  private disposed = false
  private pending = new Map<number, Pending>()

  async run(task: EmbeddedWorkerTask, payload: EmbeddedWorkerPayload, timeoutMs = 4000) {
    if (this.disposed) throw new Error('工具组件已离开当前页面。')
    const worker = this.ensureWorker()
    const id = ++this.nextId
    return new Promise<EmbeddedWorkerResult>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id)
        this.rebuild(new Error('正则运行超过 250ms，已终止本次计算并重建 Worker。'))
        reject(new Error('正则运行超过 250ms，已终止本次计算并重建 Worker。'))
      }, timeoutMs)
      this.pending.set(id, { resolve, reject, timer })
      worker.postMessage({ id, task, payload })
    })
  }

  destroy() {
    this.disposed = true
    this.rejectPending(new Error('工具组件已销毁，Worker 已终止。'))
    this.worker?.terminate()
    this.worker = undefined
  }

  private ensureWorker() {
    if (this.worker) return this.worker
    if (typeof Worker === 'undefined') throw new Error('当前浏览器不支持 Web Worker。')
    const worker = new Worker(new URL('../workers/embedded-tool.worker.ts', import.meta.url), { type: 'module' })
    worker.onmessage = (event: MessageEvent<WorkerResponse>) => {
      const pending = this.pending.get(event.data.id)
      if (!pending) return
      this.pending.delete(event.data.id)
      clearTimeout(pending.timer)
      if (event.data.ok && event.data.result) pending.resolve(event.data.result)
      else pending.reject(new Error(event.data.error || '工具运行失败，请检查输入。'))
    }
    worker.onerror = () => this.rebuild(new Error('工具 Worker 发生错误，已重建，请重试。'))
    this.worker = worker
    return worker
  }

  private rebuild(reason: Error) {
    this.worker?.terminate()
    this.worker = undefined
    this.rejectPending(reason)
    if (!this.disposed) this.ensureWorker()
  }

  private rejectPending(reason: Error) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer)
      pending.reject(reason)
    }
    this.pending.clear()
  }
}

export function useEmbeddedWorker() {
  const client = new EmbeddedWorkerClient()
  onBeforeUnmount(() => client.destroy())
  return client
}
