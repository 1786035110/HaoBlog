export default defineNitroPlugin(nitroApp => {
  nitroApp.hooks.hook('render:html', htmlContext => {
    // 公开阅读首屏不预取 Studio 与 Mermaid 路由块，避免把非首屏依赖变成网络请求。
    htmlContext.head = htmlContext.head.map(chunk => chunk.replace(/<link rel="prefetch"[^>]*>/g, ''))
  })
})
