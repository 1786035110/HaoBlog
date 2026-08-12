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
corepack pnpm install --frozen-lockfile
corepack pnpm --dir apps/web typecheck
corepack pnpm --dir apps/web test
corepack pnpm --dir apps/web build
docker compose --env-file .env -f infra/compose/compose.dev.yml config
```

The initial slice contains only an API health/public-site probe, a static SSR shell, and the PostgreSQL/pgvector development container. Articles, authentication, comments, AI, OSS, and complex visual features are intentionally not implemented.
