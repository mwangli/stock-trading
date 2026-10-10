# AI_GENERATE_START -
ARG NODE_IMAGE=node:22.19.0-alpine
ARG NGINX_IMAGE=nginx:1.28.0-alpine

FROM ${NODE_IMAGE} AS builder

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM ${NGINX_IMAGE}

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O - http://127.0.0.1/healthz >/dev/null 2>&1 || exit 1
# AI_GENERATE_END -
