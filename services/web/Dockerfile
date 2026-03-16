FROM node:24.14.0-bookworm-slim AS base
WORKDIR /app

FROM base AS deps
COPY package.json package-lock.json* .env ./
RUN --mount=type=cache,target=/root/.npm \
  if [ -f package-lock.json ]; then npm ci; else npm install; fi

FROM base AS builder
ENV NEXT_TELEMETRY_DISABLED=1
ENV BUILD_STANDALONE=true
COPY --from=deps /app/node_modules ./node_modules
COPY package.json package-lock.json* tsconfig.json next-env.d.ts next.config.ts eslint.config.mjs .env ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM node:24.14.0-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
RUN addgroup --system --gid 1001 nodejs \
  && adduser --system --uid 1001 --ingroup nodejs nextjs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
