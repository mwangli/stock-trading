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

set_env_value() {
  local key="$1"
  local value="$2"
  local temp_file
  temp_file="$(mktemp)"
  awk -F= -v key="$key" -v value="$value" '
    BEGIN { found = 0 }
    $1 == key { print key "=" value; found = 1; next }
    { print }
    END { if (!found) print key "=" value }
  ' .env > "$temp_file"
  mv "$temp_file" .env
}

normalize_data_dir() {
  local key="$1"
  local container_name="$2"
  local container_path="$3"
  local default_path="$4"
  local current
  local existing_source

  current="$(env_value "$key")"
  if [[ "$current" =~ ^[[:alpha:]]:[/\\] ]]; then
    existing_source="$(docker inspect --format "{{range .Mounts}}{{if eq .Destination \"$container_path\"}}{{.Source}}{{end}}{{end}}" "$container_name" 2>/dev/null || true)"
    if [[ "$existing_source" == /* ]]; then
      current="$existing_source"
    else
      current="$DEPLOY_DIR/$default_path"
    fi
    set_env_value "$key" "$current"
    echo "Normalized $key to Linux path: $current"
  fi

  mkdir -p "${current:-$DEPLOY_DIR/$default_path}"
}

ensure_secret MYSQL_PASSWORD
ensure_secret REDIS_PASSWORD
ensure_secret INTERNAL_API_TOKEN

normalize_data_dir MYSQL_DATA_DIR stock-mysql /var/lib/mysql data/mysql
normalize_data_dir MONGO_DATA_DIR stock-mongo /data/db data/mongo
normalize_data_dir REDIS_DATA_DIR stock-redis /data data/redis

docker compose --env-file .env config --quiet

infrastructure_services=(stock-mysql stock-mongo stock-redis)
docker compose --env-file .env pull "${infrastructure_services[@]}"
docker compose --env-file .env up -d --no-build "${infrastructure_services[@]}"

for service_name in "${infrastructure_services[@]}"; do
  container_id="$(docker compose --env-file .env ps -q "$service_name")"
  test -n "$container_id"

  healthy=0
  for _ in $(seq 1 36); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
    if [[ "$status" == "healthy" ]]; then
      healthy=1
      break
    fi
    if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
      break
    fi
    sleep 5
  done

  if [[ "$healthy" -ne 1 ]]; then
    docker compose --env-file .env logs --tail=100 "$service_name" || true
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

echo "Production prerequisites are ready."
