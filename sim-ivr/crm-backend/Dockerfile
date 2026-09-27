# --- build stage ---
FROM node:22-slim AS build
WORKDIR /app

COPY package*.json ./
COPY prisma ./prisma
RUN npm ci

COPY tsconfig.json ./
COPY scripts ./scripts
COPY src ./src
RUN npm run build

# --- runtime stage ---
FROM node:22-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY package*.json ./
COPY prisma ./prisma
# Full install (not --omit=dev): the `prisma` CLI itself is a devDependency but is needed at
# container startup to run `prisma migrate deploy` against whatever database DATABASE_URL points to.
RUN npm ci && npx prisma generate

COPY --from=build /app/dist ./dist

EXPOSE 8080
USER node

CMD ["sh", "-c", "npx prisma migrate deploy && node dist/server.js"]
