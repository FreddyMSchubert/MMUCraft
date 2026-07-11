FROM node:24-bookworm-slim

WORKDIR /app

COPY services/api/package.json ./
COPY services/api/package-lock.json ./
RUN npm install

COPY services/api/tsconfig.json ./
COPY services/api/drizzle.config.ts ./
COPY services/api/src ./src
COPY services/api/drizzle ./drizzle
COPY services/api/content ./content
COPY services/web/public/knowledge ./content/knowledge
COPY minecraft/main/data/data/items ./content/items
COPY proto ./proto

ENV HOST=0.0.0.0
ENV PORT=8080

EXPOSE 8080
EXPOSE 50051

CMD ["npm", "run", "dev"]
