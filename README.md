# HaoBlog

HaoBlog is a Java 21 and Nuxt 4 modular-monolith developer blog. The public site is SSR-first; `/studio` is client-rendered.

## Local prerequisites

- Java 21
- Maven Wrapper (included in `apps/api`)
- Node.js 24 LTS
- pnpm 11.16.0
- Docker Desktop with Compose

Copy `.env.example` to `.env` and replace the local database password before starting PostgreSQL.

## 最短命令

Windows 11 PowerShell：

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f infra/compose/compose.dev.yml up -d
pnpm install --frozen-lockfile
pnpm --filter @haoblog/api-client generate
pnpm --dir apps/web typecheck
pnpm --dir apps/web test
pnpm --dir apps/web build
pnpm smoke
docker compose --env-file .env -f infra/compose/compose.dev.yml down -v
docker compose --env-file .env -f infra/compose/compose.dev.yml logs --tail=100
cd apps/api; .\mvnw.cmd test; .\mvnw.cmd verify; cd ../..
```

通用 Shell：

```sh
cp .env.example .env
docker compose --env-file .env -f infra/compose/compose.dev.yml up -d
pnpm install --frozen-lockfile
pnpm --filter @haoblog/api-client generate
pnpm --dir apps/web typecheck && pnpm --dir apps/web test && pnpm --dir apps/web build
pnpm smoke
docker compose --env-file .env -f infra/compose/compose.dev.yml down -v
docker compose --env-file .env -f infra/compose/compose.dev.yml logs --tail=100
cd apps/api && ./mvnw test && ./mvnw verify
```

开发 Compose 提供 PostgreSQL/pgvector、API 和 Web；API/Web 构建 context 均为仓库根目录。生产 Compose 只允许 Caddy 对外开放 80/443，数据库和 Actuator 不暴露宿主机端口。生产镜像、域名和密码必须通过外部 env 文件注入，不能使用仓库中的示例值。

故障排查：

- 端口占用：`Get-NetTCPConnection -LocalPort 3000,5432,8080`；Shell 使用 `ss -ltnp | grep -E ':3000|:5432|:8080'`。
- 容器健康：`docker compose --env-file .env -f infra/compose/compose.dev.yml ps`。
- 查看日志：`docker compose --env-file .env -f infra/compose/compose.dev.yml logs --tail=100 api web postgres`。
- 数据库迁移失败：先查看 API 日志和 PostgreSQL 健康状态，再执行 `docker compose --env-file .env -f infra/compose/compose.dev.yml down -v` 清理本地开发 volume 后重试。
- Web 无法连接 API：确认 API 健康后检查容器内地址 `http://api:8080`，不要在 Compose 内使用 `localhost`。

`mvn test` runs unit tests through Surefire without Docker. `mvn verify` additionally runs PostgreSQL/pgvector Testcontainers integration tests through Failsafe and therefore requires Docker. The current backend baseline exposes health, site settings and a published-article list. Authentication, comments, AI, OSS, and complex visual features are intentionally not implemented.
