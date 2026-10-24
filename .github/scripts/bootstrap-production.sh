#!/usr/bin/env bash
set -Eeuo pipefail

cd "${DEPLOY_DIR:?DEPLOY_DIR is required}"
test -f .env
test -f docker-compose.yml
chmod 600 .env

env_value() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' .env
}

ensure_secret() {
  local key="$1"
  local current
  current="$(env_value "$key")"
  if [[ -z "$current" ]]; then
    printf '%s=%s\n' "$key" "$(openssl rand -hex 32)" >> .env
    echo "Added missing production variable: $key"
  fi
}

ensure_secret MYSQL_PASSWORD
ensure_secret REDIS_PASSWORD
ensure_secret INTERNAL_API_TOKEN

mysql_data_dir="$(env_value MYSQL_DATA_DIR)"
mongo_data_dir="$(env_value MONGO_DATA_DIR)"
redis_data_dir="$(env_value REDIS_DATA_DIR)"
mkdir -p "${mysql_data_dir:-./data/mysql}" "${mongo_data_dir:-./data/mongo}" "${redis_data_dir:-./data/redis}"

docker compose --env-file .env config --quiet

for container in stock-mysql stock-mongo; do
  state="$(docker inspect --format '{{.State.Status}}' "$container" 2>/dev/null || true)"
  if [[ "$state" != "running" ]]; then
    echo "Required database container is not running: $container"
    exit 1
  fi
done

mysql_root_password="$(env_value MYSQL_ROOT_PASSWORD)"
mysql_password="$(env_value MYSQL_PASSWORD)"
test -n "$mysql_root_password"
test -n "$mysql_password"
docker exec \
  -e MYSQL_PWD="$mysql_root_password" \
  stock-mysql mysql --protocol=socket -uroot \
  -e "CREATE DATABASE IF NOT EXISTS stock_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'stock'@'%' IDENTIFIED BY '$mysql_password'; ALTER USER 'stock'@'%' IDENTIFIED BY '$mysql_password'; GRANT ALL PRIVILEGES ON stock_trading.* TO 'stock'@'%'; FLUSH PRIVILEGES;"

docker compose --env-file .env pull stock-redis
docker compose --env-file .env up -d --no-deps stock-redis

redis_id="$(docker compose --env-file .env ps -q stock-redis)"
test -n "$redis_id"
for _ in $(seq 1 24); do
  redis_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$redis_id")"
  if [[ "$redis_health" == "healthy" ]]; then
    echo "Production prerequisites are ready."
    exit 0
  fi
  if [[ "$redis_health" == "unhealthy" || "$redis_health" == "exited" || "$redis_health" == "dead" ]]; then
    break
  fi
  sleep 5
done

docker compose --env-file .env logs --tail=100 stock-redis || true
exit 1
