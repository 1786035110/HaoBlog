# HaoBlog

HaoBlog 是一个面向开发者的低成本个人博客与数字花园，采用 Java 21、Spring Boot、Nuxt 4、Vue 3 和 PostgreSQL/pgvector 构建。项目使用模块化单体架构，公开站点以服务端渲染为核心，管理后台通过 `/studio` 提供内容运营能力。

项目以“极夜观测站”为设计主题，关注长文阅读、内容可发现性和低性能主机上的稳定运行。它提供文章、中文搜索、即时评论、工具箱、RSS、Sitemap、目录、结构化数据、知识星图、安全终端、双主题、可关闭的首页 Three.js、音乐控制台与频谱、404 游戏中心和显式准备的 PWA 离线工具箱。公开内容保持 SSR；无 JavaScript、Save-Data、reduced-motion、触摸和重型能力失败时均保留可读路径。

## 本地验证

仓库要求 Java 21、Node.js 24.x、Corepack 管理的 pnpm 11.16.0，以及包含 Compose 的 Docker Desktop。常用检查从仓库根目录执行：

```powershell
Push-Location apps/api
.\mvnw.cmd -DskipITs verify
.\mvnw.cmd failsafe:integration-test failsafe:verify
Pop-Location

corepack pnpm --dir apps/web typecheck
corepack pnpm --dir apps/web test
corepack pnpm --dir apps/web build
corepack pnpm --filter @haoblog/api-client generate
corepack pnpm --filter @haoblog/api-client check
corepack pnpm web:budget
corepack pnpm compose:verify
```

阶段五音乐默认关闭。清单 URL 可在 Studio 配置，`HAOBLOG_MUSIC_MANIFEST_URL` 是数据库未配置时的回退值；生产环境必须使用绝对 HTTPS，并为浏览器直连清单与音频配置 CORS。音频不经过 Spring Boot。PWA 只在 production build 或显式测试模式注册，进入 `/tools` 后还需点击“准备离线工具”并二次确认；缓存不包含 API、Studio、文章、评论、图谱、音乐或外部资源。

完整开发环境、独立生产 Compose 验收、Lighthouse 串行路由和清理命令见 [本地启动指南](docs/本地启动指南.md)。阶段目标任务和准入清单属于本地私有验收资料，不纳入公开仓库。本地收口不等于远端 CI、真实 OSS/CDN 与 CORS、真实域名/HTTPS 安装、部署备案或阶段六 30 分钟 2GB 压测通过。

生产 API/Web 使用只读根目录和 32/16 MiB 临时目录，四容器禁用容器 Swap，保留原内存/CPU/PID 预算。业务连接获取等待 2 秒、SQL 3 秒、锁等待 1 秒；Flyway 使用独立连接。SMTP/OSS 在数据库事务外执行，评论通知逐条认领、最多 5 次尝试，采用至少一次语义。运行态资源验证与权限例外见启动指南。
