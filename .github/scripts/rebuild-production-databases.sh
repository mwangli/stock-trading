#!/usr/bin/env bash
set -Eeuo pipefail

readonly database_password="${DATABASE_PASSWORD:?DATABASE_PASSWORD is required}"
readonly infrastructure_services=(stock-mysql stock-mongo stock-redis)
readonly application_services=(stock-backend stock-agent)

if [[ ! "$database_password" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Database password contains unsupported characters."
  exit 1
fi

cd "${DEPLOY_DIR:?DEPLOY_DIR is required}"
test -f .env
test -f docker-compose.yml
chmod 600 .env

env_value() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' .env
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
  chmod 600 .env
}

remove_env_values() {
  local temp_file
  temp_file="$(mktemp)"
  awk -F= '
    $1 != "MYSQL_ROOT_PASSWORD" &&
    $1 != "MYSQL_PASSWORD" &&
    $1 != "MONGO_ROOT_PASSWORD" &&
    $1 != "REDIS_PASSWORD"
  ' .env > "$temp_file"
  mv "$temp_file" .env
  chmod 600 .env
}

container_env_value() {
  local container_name="$1"
  local key="$2"
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container_name" \
    | awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}'
}

container_mount_source() {
  local container_name="$1"
  local destination="$2"
  docker inspect --format "{{range .Mounts}}{{if eq .Destination \"$destination\"}}{{.Source}}{{end}}{{end}}" "$container_name"
}

wait_for_health() {
  local service_name="$1"
  local container_id
  local status
  container_id="$(docker compose --env-file .env ps -q "$service_name")"
  test -n "$container_id"

  for _ in $(seq 1 72); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
    if [[ "$status" == "healthy" ]]; then
      return 0
    fi
    if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
      break
    fi
    sleep 5
  done

  docker compose --env-file .env logs --tail=200 "$service_name" || true
  return 1
}

for service_name in "${infrastructure_services[@]}"; do
  docker inspect "$service_name" >/dev/null
done

mysql_data_source="$(container_mount_source stock-mysql /var/lib/mysql)"
mongo_data_source="$(container_mount_source stock-mongo /data/db)"
redis_data_source="$(container_mount_source stock-redis /data)"
test -n "$mysql_data_source"
test -n "$mongo_data_source"
test -n "$redis_data_source"

old_mysql_root_password="$(container_env_value stock-mysql MYSQL_ROOT_PASSWORD)"
old_mongo_root_password="$(container_env_value stock-mongo MONGO_INITDB_ROOT_PASSWORD)"
test -n "$old_mysql_root_password"
test -n "$old_mongo_root_password"

docker exec -e MYSQL_PWD="$old_mysql_root_password" stock-mysql \
  mysql --protocol=socket -uroot --database=mysql --execute \
  "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '$database_password'; ALTER USER 'root'@'%' IDENTIFIED BY '$database_password'; GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION; ALTER USER 'root'@'localhost' IDENTIFIED BY '$database_password'; CREATE USER IF NOT EXISTS 'stock'@'%' IDENTIFIED BY '$database_password'; ALTER USER 'stock'@'%' IDENTIFIED BY '$database_password'; GRANT ALL PRIVILEGES ON stock_trading.* TO 'stock'@'%'; FLUSH PRIVILEGES;"

docker exec \
  -e MONGO_OLD_PASSWORD="$old_mongo_root_password" \
  -e MONGO_NEW_PASSWORD="$database_password" \
  stock-mongo mongosh --quiet \
  --username admin \
  --password "$old_mongo_root_password" \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("admin").updateUser("admin", {pwd: process.env.MONGO_NEW_PASSWORD})'

set_env_value DATABASE_PASSWORD "$database_password"
remove_env_values

backend_image="$(docker inspect --format '{{.Config.Image}}' stock-backend 2>/dev/null || true)"
agent_image="$(docker inspect --format '{{.Config.Image}}' stock-agent 2>/dev/null || true)"
test -n "$backend_image"
test -n "$agent_image"
export STOCK_BACKEND_IMAGE="$backend_image"
export STOCK_AGENT_IMAGE="$agent_image"

docker compose --env-file .env config --quiet
docker compose --env-file .env pull "${infrastructure_services[@]}"
docker compose --env-file .env up -d --no-deps --no-build --force-recreate "${infrastructure_services[@]}"

for service_name in "${infrastructure_services[@]}"; do
  wait_for_health "$service_name"
done

test "$(container_mount_source stock-mysql /var/lib/mysql)" = "$mysql_data_source"
test "$(container_mount_source stock-mongo /data/db)" = "$mongo_data_source"
test "$(container_mount_source stock-redis /data)" = "$redis_data_source"

docker compose --env-file .env up -d --no-deps --no-build --force-recreate "${application_services[@]}"
for service_name in "${application_services[@]}"; do
  wait_for_health "$service_name"
done

docker exec -e MYSQL_PWD="$database_password" stock-mysql \
  mysql --protocol=tcp -h127.0.0.1 -uroot --execute 'SELECT 1;'
docker exec -e MONGO_PASSWORD="$database_password" stock-mongo \
  mongosh --quiet --host 127.0.0.1 --username admin --password "$database_password" \
  --authenticationDatabase admin --eval 'quit(db.adminCommand({ping: 1}).ok ? 0 : 2)'
docker exec -e REDISCLI_AUTH="$database_password" stock-redis redis-cli -h 127.0.0.1 ping \
  | grep -q '^PONG$'

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q '^Status: active'; then
  ufw allow 6033/tcp
  ufw allow 27017/tcp
  ufw allow 6379/tcp
fi

if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port=6033/tcp
  firewall-cmd --permanent --add-port=27017/tcp
  firewall-cmd --permanent --add-port=6379/tcp
  firewall-cmd --reload
fi

if command -v iptables >/dev/null 2>&1; then
  for port in 6033 27017 6379; do
    iptables -C INPUT -p tcp --dport "$port" -j ACCEPT >/dev/null 2>&1 \
      || iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
    if iptables -nL DOCKER-USER >/dev/null 2>&1; then
      iptables -C DOCKER-USER -p tcp --dport "$port" -j ACCEPT >/dev/null 2>&1 \
        || iptables -I DOCKER-USER 1 -p tcp --dport "$port" -j ACCEPT
    fi
  done

  if command -v netfilter-persistent >/dev/null 2>&1; then
    netfilter-persistent save
  fi
fi

docker compose --env-file .env ps "${infrastructure_services[@]}" "${application_services[@]}"
ss -lnt | awk '$4 ~ /:(6033|27017|6379)$/ {print}'
echo "Production databases were rebuilt through Docker Compose and passed authenticated server-side checks."
