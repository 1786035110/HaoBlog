import { defineAsyncComponent, type Component } from 'vue'

export const embeddedToolComponents: Record<string, Component> = {
  'json-format': defineAsyncComponent(() => import('~/components/tools/JsonFormatTool.vue')),
  base64: defineAsyncComponent(() => import('~/components/tools/Base64Tool.vue')),
  'url-codec': defineAsyncComponent(() => import('~/components/tools/UrlCodecTool.vue')),
  timestamp: defineAsyncComponent(() => import('~/components/tools/TimestampTool.vue')),
  'regex-test': defineAsyncComponent(() => import('~/components/tools/RegexTestTool.vue')),
}

export type EmbeddedComponentKey = keyof typeof embeddedToolComponents

export function getEmbeddedToolComponent(componentKey: string | null | undefined) {
  return componentKey ? embeddedToolComponents[componentKey] : undefined
}
