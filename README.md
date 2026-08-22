# HaoBlog

HaoBlog is a Java 21 and Nuxt 4 modular-monolith developer blog. The public site is SSR-first; `/studio` is client-rendered. 阶段三已形成“极夜观测站”公开阅读体验，包含安全高级 Markdown、TOC、SEO/JSON-LD、RSS、Sitemap、无 JavaScript 与数据节省路径。

## Local prerequisites

- Java 21
- Maven Wrapper (included in `apps/api`)
- Node.js 24 LTS
- pnpm 11.16.0
- Docker Desktop with Compose

Copy `.env.example` to `.env` and replace the local database password before starting PostgreSQL.

完整的环境准备、管理员 BCrypt 配置、Compose 启动、直接运行 API/Web、验证命令和故障排查请参阅：[本地启动指南](docs/本地启动指南.md)。

## 最短命令

Windows 11 PowerShell：

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f infra/compose/compose.dev.yml up -d
corepack pnpm install --frozen-lockfile
corepack pnpm --filter @haoblog/api-client generate
corepack pnpm --filter @haoblog/api-client check
corepack pnpm compose:verify
corepack pnpm --dir apps/web typecheck
corepack pnpm --dir apps/web test
corepack pnpm --dir apps/web build
corepack pnpm web:budget
corepack pnpm smoke
docker compose --env-file .env -f infra/compose/compose.dev.yml logs --tail=100
docker compose --env-file .env -f infra/compose/compose.dev.yml down -v
cd apps/api; .\mvnw.cmd -DskipITs verify; .\mvnw.cmd failsafe:integration-test failsafe:verify; cd ../..
```

通用 Shell：

```sh
cp .env.example .env
docker compose --env-file .env -f infra/compose/compose.dev.yml up -d
corepack pnpm install --frozen-lockfile
corepack pnpm --filter @haoblog/api-client generate
corepack pnpm --filter @haoblog/api-client check
corepack pnpm compose:verify
corepack pnpm --dir apps/web typecheck && corepack pnpm --dir apps/web test && corepack pnpm --dir apps/web build
corepack pnpm web:budget
corepack pnpm smoke
docker compose --env-file .env -f infra/compose/compose.dev.yml logs --tail=100
docker compose --env-file .env -f infra/compose/compose.dev.yml down -v
cd apps/api && ./mvnw -DskipITs verify && ./mvnw failsafe:integration-test failsafe:verify
```

开发 Compose 提供 PostgreSQL/pgvector、API 和 Web；API/Web 构建 context 均为仓库根目录。生产 Compose 只允许 Caddy 对外开放 80/443，数据库和 Actuator 不暴露宿主机端口。生产镜像、域名和密码必须通过外部 env 文件注入，不能使用仓库中的示例值。

故障排查：

- 端口占用：`Get-NetTCPConnection -LocalPort 3000,5432,8080`；Shell 使用 `ss -ltnp | grep -E ':3000|:5432|:8080'`。
- 容器健康：`docker compose --env-file .env -f infra/compose/compose.dev.yml ps`。
- 查看日志：`docker compose --env-file .env -f infra/compose/compose.dev.yml logs --tail=100 api web postgres`。
- 数据库迁移失败：先查看 API 日志和 PostgreSQL 健康状态，再执行 `docker compose --env-file .env -f infra/compose/compose.dev.yml down -v` 清理本地开发 volume 后重试。
- Web 无法连接 API：确认 API 健康后检查容器内地址 `http://api:8080`，不要在 Compose 内使用 `localhost`。

阶段三验收顺序是 API `-DskipITs verify`、Web typecheck/test/build、OpenAPI 生成一致性、dev/prod Compose 配置、资源边界、PostgreSQL/pgvector Failsafe、生产四容器健康、Smoke、Chromium Playwright、文章客户端预算、Lighthouse 和 `git diff --check`。生产编排的可复制命令与实际完成状态见 [阶段三准入清单](docs/阶段三准入清单.md)。Playwright 使用仓库测试账号 `admin/password`，应配合 `infra/compose/.env.ci.example` 的临时栈运行，不能用于生产。评论、工具箱功能、AI、RAG、Three.js、终端和部署仍不在阶段三范围内。
