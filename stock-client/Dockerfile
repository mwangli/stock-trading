# AI_GENERATE_START -------
FROM node:22.22.3-alpine AS dependencies

WORKDIR /workspace/stock-client

COPY stock-client/package.json stock-client/package-lock.json stock-client/.npmrc ./
RUN npm ci

FROM dependencies AS app-build

COPY stock-client/tsconfig*.json ./
COPY stock-client/vite.config.ts stock-client/postcss.config.js stock-client/tailwind.config.js stock-client/index.html ./
COPY stock-client/public/ ./public/
COPY stock-client/src/ ./src/
COPY documents/ /workspace/documents/
RUN npm run build

FROM nginx:1.27-alpine

COPY stock-client/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=app-build /workspace/stock-client/dist/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
# AI_GENERATE_END -------
