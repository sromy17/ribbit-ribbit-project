FROM node:24-alpine
WORKDIR /app/shared/auth-stub
COPY shared/auth-stub/package.json shared/auth-stub/package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
WORKDIR /app
COPY migrations ./migrations
COPY scripts/local-instrument-catalog.cjs ./scripts/local-instrument-catalog.cjs
COPY frontend/public/data/us-v1.json ./frontend/public/data/us-v1.json
COPY deploy/migrate.cjs ./deploy/migrate.cjs
USER node
CMD ["node", "deploy/migrate.cjs"]
