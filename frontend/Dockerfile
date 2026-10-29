FROM node:24-alpine AS build
WORKDIR /app
ENV NG_CLI_ANALYTICS=false NG_BUILD_MAX_WORKERS=2
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Normalize the Angular build output directory.
# Modern Angular projects often output to dist/<project>/browser.
RUN set -eux; \
    mkdir -p /output; \
    if [ -d dist/frontend/browser ]; then \
      cp -a dist/frontend/browser/. /output/; \
    elif [ -d dist/frontend ]; then \
      cp -a dist/frontend/. /output/; \
    else \
      echo "Angular build output not found. Check angular.json outputPath."; \
      find dist -maxdepth 4 -type f | head -50; \
      exit 1; \
    fi

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/health || exit 1
