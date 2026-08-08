# syntax=docker/dockerfile:1.7

ARG NODE_VERSION=24.14.0
FROM node:${NODE_VERSION}-bookworm-slim AS base

WORKDIR /app
ENV NPM_CONFIG_UPDATE_NOTIFIER=false \
    NPM_CONFIG_FUND=false

FROM base AS deps
COPY services/api/package.json services/api/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

FROM deps AS build
COPY services/api/tsconfig.json services/api/drizzle.config.ts ./
COPY services/api/src ./src
COPY services/api/scripts ./scripts
COPY services/api/drizzle ./drizzle
RUN npm test

FROM deps AS dev
ENV NODE_ENV=development \
    HOST=0.0.0.0 \
    PORT=8080
COPY services/api/tsconfig.json services/api/drizzle.config.ts ./
COPY services/api/src ./src
COPY services/api/scripts ./scripts
COPY services/api/drizzle ./drizzle
COPY services/web/public/knowledge ./content/knowledge
COPY minecraft/main/data/data/items ./content/items
COPY minecraft/main/respack/packs/general-pack/assets/general-pack/textures/item/wallet/wallet-0.png ./content/items/charm/held/wallet/texture.png
COPY proto ./proto
EXPOSE 8080 50051
CMD ["npm", "run", "dev"]

FROM base AS prod-deps
COPY services/api/package.json services/api/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev

FROM base AS runner
ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=8080 \
    NODE_OPTIONS=--max-old-space-size=512
COPY --from=prod-deps --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/dist ./dist
COPY --chown=node:node services/api/drizzle ./drizzle
COPY --chown=node:node services/web/public/knowledge ./content/knowledge
COPY --chown=node:node minecraft/main/data/data/items ./content/items
COPY --chown=node:node minecraft/main/respack/packs/general-pack/assets/general-pack/textures/item/wallet/wallet-0.png ./content/items/charm/held/wallet/texture.png
COPY --chown=node:node proto ./proto
RUN mkdir /app/data && chown node:node /app/data
USER node
EXPOSE 8080 50051
CMD ["node", "dist/main.js"]
