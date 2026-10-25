# AI_GENERATE_START ------
FROM node:22.22.3-alpine AS dependencies

WORKDIR /workspace/stock-frontend

COPY stock-frontend/package.json stock-frontend/package-lock.json stock-frontend/.npmrc ./
RUN npm ci

FROM dependencies AS app-build

COPY stock-frontend/tsconfig*.json ./
COPY stock-frontend/vite.config.ts stock-frontend/postcss.config.js stock-frontend/tailwind.config.js stock-frontend/index.html ./
COPY stock-frontend/public/ ./public/
COPY stock-frontend/src/ ./src/
COPY documents/ /workspace/documents/
RUN npm run build

FROM nginx:1.27-alpine

COPY stock-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=app-build /workspace/stock-frontend/dist/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
# AI_GENERATE_END ------
