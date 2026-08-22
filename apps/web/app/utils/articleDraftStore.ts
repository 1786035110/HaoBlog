import type { ArticleFormModel } from './studioArticleForm'

export type ArticleDraftRecord = {
  articleId: string
  form: ArticleFormModel
  baseVersion: number
  localUpdatedAt: string
}

export type ArticleDraftStore = {
  get(articleId: string): Promise<ArticleDraftRecord | null>
  put(record: ArticleDraftRecord): Promise<void>
  delete(articleId: string): Promise<void>
}

const DATABASE = 'haoblog-studio'
const STORE = 'article-drafts'

let database: Promise<IDBDatabase> | null = null

function openDatabase() {
  if (typeof indexedDB === 'undefined') return Promise.reject(new Error('浏览器不支持 IndexedDB'))
  if (database) return database
  database = new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE, { keyPath: 'articleId' })
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error || new Error('无法打开 IndexedDB'))
  })
  return database
}

export const nativeArticleDraftStore: ArticleDraftStore = {
  async get(articleId) {
    const db = await openDatabase()
    return new Promise((resolve, reject) => {
      const request = db.transaction(STORE, 'readonly').objectStore(STORE).get(articleId)
      request.onsuccess = () => resolve((request.result as ArticleDraftRecord | undefined) || null)
      request.onerror = () => reject(request.error || new Error('无法读取本地副本'))
    })
  },
  async put(record) {
    const db = await openDatabase()
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, 'readwrite')
      transaction.objectStore(STORE).put(record)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error || new Error('无法保存本地副本'))
      transaction.onabort = () => reject(transaction.error || new Error('本地副本保存已中止'))
    })
  },
  async delete(articleId) {
    const db = await openDatabase()
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, 'readwrite')
      transaction.objectStore(STORE).delete(articleId)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error || new Error('无法删除本地副本'))
      transaction.onabort = () => reject(transaction.error || new Error('本地副本删除已中止'))
    })
  },
}
