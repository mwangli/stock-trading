# AI_GENERATE_START ----------
FROM node:22.22.3-alpine AS app-build

WORKDIR /workspace/stock-web

COPY stock-web/package.json stock-web/package-lock.json stock-web/.npmrc ./
RUN npm ci --no-audit --no-fund

COPY stock-web/tsconfig*.json ./
COPY stock-web/vite.config.ts stock-web/index.html stock-web/mobile-demo.html ./
COPY stock-web/public/ ./public/
COPY stock-web/src/ ./src/
RUN npm run build

FROM nginx:1.27-alpine

COPY stock-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=app-build /workspace/stock-web/dist/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
# AI_GENERATE_END ----------
