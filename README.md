# HaoBlog

HaoBlog is a Java 21 and Nuxt 4 modular-monolith developer blog. The public site is SSR-first; `/studio` is client-rendered.

## Local prerequisites

- Java 21
- Maven Wrapper (included in `apps/api`)
- Node.js 24 LTS
- Corepack with pnpm 11.16.0
- Docker Desktop with Compose

Copy `.env.example` to `.env` and replace the local database password before starting PostgreSQL.

## Commands

```powershell
docker compose --env-file .env -f infra/compose/compose.dev.yml up -d
cd apps/api; .\mvnw.cmd test; cd ../..
cd apps/api; .\mvnw.cmd verify; cd ../..
corepack pnpm install --frozen-lockfile
corepack pnpm --filter @haoblog/api-client generate
corepack pnpm --dir apps/web typecheck
corepack pnpm --dir apps/web test
corepack pnpm --dir apps/web build
docker compose --env-file .env -f infra/compose/compose.dev.yml config
```

`mvn test` runs unit tests through Surefire without Docker. `mvn verify` additionally runs PostgreSQL/pgvector Testcontainers integration tests through Failsafe and therefore requires Docker. The current backend baseline exposes health, site settings and a published-article list. Authentication, comments, AI, OSS, and complex visual features are intentionally not implemented.
