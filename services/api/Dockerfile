FROM node:24-bookworm-slim

WORKDIR /app

COPY package.json ./
COPY package-lock.json ./
RUN npm install

COPY src ./src

ENV HOST=0.0.0.0
ENV PORT=8080

EXPOSE 8080

CMD ["npm", "run", "dev"]
