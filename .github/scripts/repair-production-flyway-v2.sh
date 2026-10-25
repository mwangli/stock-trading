#!/usr/bin/env bash
set -Eeuo pipefail

readonly expected_old_checksum="1627667484"
readonly expected_new_checksum="-735681307"
readonly migration_version="2"
readonly dml_file="${1:?V2 DML file is required}"

cd "${DEPLOY_DIR:?DEPLOY_DIR is required}"
test -f docker-compose.yml
test -f .env
test -s "$dml_file"
docker inspect stock-mysql >/dev/null
docker inspect stock-backend >/dev/null

mysql_exec() {
  docker exec -i stock-mysql sh -c \
    'exec mysql --batch --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" stock_trading'
}

read_migration_state() {
  mysql_exec <<SQL
SELECT COUNT(*),
       COALESCE(MIN(checksum), 0),
       COALESCE(MAX(checksum), 0),
       COALESCE(SUM(success = 1), 0)
FROM flyway_schema_history
WHERE version = '${migration_version}';
SQL
}

expected_old_state=$'1\t'"${expected_old_checksum}"$'\t'"${expected_old_checksum}"$'\t1'
old_state="$(read_migration_state)"
if [[ "$old_state" != "$expected_old_state" ]]; then
  echo "Flyway V2 state does not match the authorized repair precondition."
  echo "Expected one successful V2 row with the authorized old checksum."
  exit 1
fi

echo "Flyway V2 precondition verified; replaying the idempotent v5.1.1 DML."
mysql_exec < "$dml_file"

updated_rows="$(mysql_exec <<SQL
UPDATE flyway_schema_history
SET checksum = ${expected_new_checksum}
WHERE version = '${migration_version}'
  AND checksum = ${expected_old_checksum}
  AND success = 1;
SELECT ROW_COUNT();
SQL
)"
if [[ "$updated_rows" != "1" ]]; then
  echo "Flyway V2 checksum update affected an unexpected number of rows: $updated_rows"
  exit 1
fi

expected_new_state=$'1\t'"${expected_new_checksum}"$'\t'"${expected_new_checksum}"$'\t1'
new_state="$(read_migration_state)"
if [[ "$new_state" != "$expected_new_state" ]]; then
  echo "Flyway V2 checksum verification failed after the repair."
  exit 1
fi

backend_image="$(docker inspect --format '{{.Config.Image}}' stock-backend)"
test -n "$backend_image"
docker image inspect "$backend_image" >/dev/null

export STOCK_BACKEND_IMAGE="$backend_image"
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --no-deps --no-build --force-recreate stock-backend

container_id="$(docker compose --env-file .env ps -q stock-backend)"
test -n "$container_id"

healthy=0
for _ in $(seq 1 72); do
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
  echo "Backend did not become healthy after the Flyway repair."
  docker compose --env-file .env logs --tail=200 stock-backend || true
  exit 1
fi

running_image="$(docker inspect --format '{{.Config.Image}}' stock-backend)"
if [[ "$running_image" != "$backend_image" ]]; then
  echo "Backend image changed unexpectedly during the Flyway repair."
  exit 1
fi

echo "Flyway V2 checksum repaired and Backend is healthy on the preserved image."
docker compose --env-file .env ps stock-backend
