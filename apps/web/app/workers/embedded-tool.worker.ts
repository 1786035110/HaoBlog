import { runEmbeddedOperation, type EmbeddedWorkerPayload, type EmbeddedWorkerTask } from '../utils/embeddedToolOperations'

type WorkerRequest = { id: number; task: EmbeddedWorkerTask; payload: EmbeddedWorkerPayload }

self.onmessage = (event: MessageEvent<WorkerRequest>) => {
  const { id, task, payload } = event.data
  try {
    self.postMessage({ id, ok: true, result: runEmbeddedOperation(task, payload) })
  } catch (error) {
    self.postMessage({ id, ok: false, error: error instanceof Error ? error.message : '工具运行失败，请检查输入。' })
  }
}
