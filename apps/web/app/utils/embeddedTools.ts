import { defineAsyncComponent, type Component } from 'vue'

export const embeddedToolComponents: Record<string, Component> = {
  'json-format': defineAsyncComponent(() => import('~/components/tools/JsonFormatTool.vue')),
  base64: defineAsyncComponent(() => import('~/components/tools/Base64Tool.vue')),
  'url-codec': defineAsyncComponent(() => import('~/components/tools/UrlCodecTool.vue')),
  timestamp: defineAsyncComponent(() => import('~/components/tools/TimestampTool.vue')),
  'regex-test': defineAsyncComponent(() => import('~/components/tools/RegexTestTool.vue')),
}

const embeddedToolLoaders = [
  () => import('~/components/tools/JsonFormatTool.vue'),
  () => import('~/components/tools/Base64Tool.vue'),
  () => import('~/components/tools/UrlCodecTool.vue'),
  () => import('~/components/tools/TimestampTool.vue'),
  () => import('~/components/tools/RegexTestTool.vue'),
]

export async function preloadEmbeddedToolChunks() {
  await Promise.all(embeddedToolLoaders.map(loader => loader()))
}

export function embeddedWorkerAssetUrl() {
  return new URL('../workers/embedded-tool.worker.ts', import.meta.url).toString()
}

export type EmbeddedComponentKey = keyof typeof embeddedToolComponents

export function getEmbeddedToolComponent(componentKey: string | null | undefined) {
  return componentKey ? embeddedToolComponents[componentKey] : undefined
}
