# HaoBlog Repository Guide

## Mission

Build HaoBlog as a low-cost developer blog and digital garden using Java and Vue. Preserve content readability, a distinctive “极夜观测站” design, and stable operation on a 2-core/2-GB production host.

## Sources of truth

- Current user request defines the task scope.
- `docs/HaoBlog 完整开发计划.md` defines architecture, modules, resource budgets, APIs, data and milestones.
- `docs/HaoBlog-design-language.md` defines public-site UI/UX, tokens, layout, motion and accessibility.
- For public-site design work, use the repo skill at `.agents/skills/haoblog-design/SKILL.md` when available.
- If sources conflict, report the conflict and propose the smallest safe resolution. Do not silently override a locked decision.

## Expected layout

- `apps/api`: Java 21 Spring Boot modular monolith.
- `apps/web`: Nuxt 4, Vue 3 and TypeScript; public SSR and `/studio` CSR.
- `packages/api-client`: reserved package for generated OpenAPI TypeScript types/client.
- `infra/compose/compose.dev.yml`: local PostgreSQL + pgvector development Compose configuration.
- `.github/workflows/ci.yml`: API, Web and Compose configuration CI.
- `docs`: architecture and design documents.

Do not create additional services unless the user changes the architecture.

## Locked architecture

- Use Java 21, Spring Boot, Spring MVC, Spring Security, PostgreSQL, pgvector, Flyway and Maven Wrapper.
- Use Nuxt 4, Vue 3, TypeScript and pnpm with the root `pnpm-workspace.yaml`, root `package.json` package-manager pin, and committed root `pnpm-lock.yaml`.
- Keep one Spring Boot process with business package boundaries: `identity`, `content`, `comment`, `toolbox`, `ai`, `media`, `site`, `shared`.
- Use REST plus SSE for AI streaming. Do not replace SSE with WebSocket without a demonstrated requirement.
- V1 must not add Redis, RabbitMQ, Kafka, Elasticsearch, Meilisearch, MinIO, a local production model or microservices.
- Production contains only Caddy, Nuxt, Spring Boot and PostgreSQL/pgvector containers.
- Verify compatible stable dependency versions from primary documentation before initial pinning; then preserve the lockfile/BOM.

## Backend conventions

- Keep controllers thin; place use cases in application/services and persistence behind module-owned repositories.
- Expose DTOs, never JPA entities.
- Use Flyway for schema changes; do not use Hibernate schema mutation in production.
- Return `application/problem+json` with `code`, `title`, `detail` and `traceId`.
- Keep `spring.jpa.open-in-view=false`; all caches, pools, queues and AI concurrency must be bounded.
- Treat article Markdown, comments, AI context, links and uploads as untrusted input.
- Keep `shared` independent of business modules and enforce boundaries with ArchUnit.

## Frontend and design conventions

- Public titles and article content must be present in SSR HTML; `/studio` may be CSR.
- The global shell is: top 1px phosphor hairline + content stage + bottom 48px instrument Dock with a 4px progress line.
- The Dock has only INDEX, command terminal and AI entry points. Do not add a traditional top navigation bar.
- Article lists use a vertical observation timeline; tools use a console switchboard. Do not use generic three-column or nine-card grids.
- Use semantic CSS tokens and `<html data-theme>`; prefer CSS-first motion.
- Three.js/GSAP/D3/Mermaid/editor code must be route- or interaction-lazy and disposed when leaving its owner route.
- Never put Three.js in the article bundle or require JavaScript to reveal first-screen content.
- Treat `prefers-reduced-motion`, Save-Data, touch input and 360px layout as first-class paths.
- Avoid large blur glassmorphism, neon outlines, gradient text, full-screen particles, 16px+ generic rounded cards and emoji icons.
- Do not install React Framer Motion in the Vue app. Select a maintained Vue-compatible motion library only when CSS is insufficient.

## API and generated code

- OpenAPI is the front-end/back-end contract.
- Generated code lives only in `packages/api-client`; do not hand-edit it or duplicate API DTO types in `apps/web`.
- Public API paths use `/api/v1/public/**`; admin paths use `/api/v1/admin/**`.
- Preserve the fixed AI SSE event names: `meta`, `delta`, `citation`, `usage`, `done`, `error`.

## Commands and validation

The current repository uses one root pnpm workspace and one Maven application. No lint script is configured yet.

- Toolchain: Node.js 24.x and pnpm 11.16.0 (matching the root `packageManager` field).
- Install Web/workspace dependencies from the repository root: `pnpm install --frozen-lockfile`
- Generate the OpenAPI client: `pnpm --filter @haoblog/api-client generate`
- API tests (Windows PowerShell): `cd apps/api; .\mvnw.cmd test`
- API tests (POSIX shell): `cd apps/api && ./mvnw test`
- API package (Windows PowerShell): `cd apps/api; .\mvnw.cmd verify`
- API package (POSIX shell): `cd apps/api && ./mvnw verify`
- Web type check: `pnpm --dir apps/web typecheck`
- Web tests: `pnpm --dir apps/web test`
- Web production build: `pnpm --dir apps/web build`
- Compose validation using placeholder environment values: `docker compose --env-file .env.example -f infra/compose/compose.dev.yml config`
- Local database startup: `docker compose --env-file .env -f infra/compose/compose.dev.yml up -d`

The Maven Wrapper is `apps/api/mvnw` / `apps/api/mvnw.cmd`, with Maven distribution `3.9.11`. The pnpm version is pinned as `pnpm@11.16.0` in the root `package.json`; the workspace also permits the explicitly configured `esbuild` build script.

Run the narrowest relevant checks while iterating, then all affected checks before handoff. Never claim a check passed unless it was run successfully; report environmental blockers precisely.

## Working rules

- Inspect existing code, instructions and Git status before editing. Preserve unrelated user changes.
- Plan before multi-module work. Implement one vertical slice at a time.
- Do not add production dependencies without stating why the current stack is insufficient and considering bundle/runtime cost.
- Do not create speculative abstractions, empty class trees or placeholder services that are not required by the current slice.
- Do not commit, push, deploy, rotate secrets or mutate cloud resources unless the user explicitly asks.
- Keep secrets out of Git, client runtime configuration, logs, fixtures and examples.
- Update documentation and this command list when behavior or the real build workflow changes.

## Definition of done

- Requested behavior is implemented without unrelated scope expansion.
- Relevant tests, type checks and builds pass.
- SSR, accessibility, responsive behavior and reduced-motion/data-saver paths are verified for public UI changes.
- Database changes include Flyway migrations and integration coverage.
- API changes update OpenAPI and generated TypeScript types reproducibly.
- Final handoff lists changed areas, commands actually run, results and remaining risks.
