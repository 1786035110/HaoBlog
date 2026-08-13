FROM node:24-bookworm-slim AS build

WORKDIR /workspace
RUN corepack enable
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/web apps/web
COPY packages/api-client packages/api-client
RUN pnpm install --frozen-lockfile
RUN pnpm --dir apps/web build

FROM node:24-bookworm-slim

WORKDIR /app
ENV NODE_ENV=production
ENV NODE_OPTIONS=--max-old-space-size=192
COPY --from=build --chown=node:node /workspace/apps/web/.output /app/.output
USER node
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=6 CMD node -e "require('http').get('http://127.0.0.1:3000/',r=>process.exit(r.statusCode>=200&&r.statusCode<500?0:1)).on('error',()=>process.exit(1))"
CMD ["node", ".output/server/index.mjs"]
