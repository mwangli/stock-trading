#!/usr/bin/env bash
set -Eeuo pipefail

readonly expected_old_checksum="1627667484"
readonly expected_new_checksum="-735681307"
readonly expected_old_description="remove startup full sync switches"
readonly expected_new_description="dml"
readonly expected_old_script="V2__remove_startup_full_sync_switches.sql"
readonly expected_new_script="V2__dml.sql"
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
       COALESCE(MIN(description), ''),
       COALESCE(MIN(type), ''),
       COALESCE(MIN(script), ''),
       COALESCE(SUM(success = 1), 0)
FROM flyway_schema_history
WHERE version = '${migration_version}';
SQL
}

expected_old_state=$'1\t'"${expected_old_checksum}"$'\t'"${expected_old_checksum}"$'\t'"${expected_old_description}"$'\tSQL\t'"${expected_old_script}"$'\t1'
expected_partial_state=$'1\t'"${expected_new_checksum}"$'\t'"${expected_new_checksum}"$'\t'"${expected_old_description}"$'\tSQL\t'"${expected_old_script}"$'\t1'
expected_new_state=$'1\t'"${expected_new_checksum}"$'\t'"${expected_new_checksum}"$'\t'"${expected_new_description}"$'\tSQL\t'"${expected_new_script}"$'\t1'
current_state="$(read_migration_state)"

if [[ "$current_state" == "$expected_old_state" || "$current_state" == "$expected_partial_state" ]]; then
  echo "Flyway V2 precondition verified; replaying the idempotent v5.1.1 DML."
  mysql_exec < "$dml_file"

  updated_rows="$(mysql_exec <<SQL
UPDATE flyway_schema_history
SET checksum = ${expected_new_checksum},
    description = '${expected_new_description}',
    type = 'SQL',
    script = '${expected_new_script}'
WHERE version = '${migration_version}'
  AND checksum IN (${expected_old_checksum}, ${expected_new_checksum})
  AND description = '${expected_old_description}'
  AND type = 'SQL'
  AND script = '${expected_old_script}'
  AND success = 1;
SELECT ROW_COUNT();
SQL
)"
  if [[ "$updated_rows" != "1" ]]; then
    echo "Flyway V2 metadata update affected an unexpected number of rows: $updated_rows"
    exit 1
  fi
elif [[ "$current_state" == "$expected_new_state" ]]; then
  echo "Flyway V2 metadata is already repaired; continuing with Backend verification."
else
  echo "Flyway V2 state does not match an authorized repair precondition."
  exit 1
fi

new_state="$(read_migration_state)"
if [[ "$new_state" != "$expected_new_state" ]]; then
  echo "Flyway V2 metadata verification failed after the repair."
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

echo "Flyway V2 metadata repaired and Backend is healthy on the preserved image."
docker compose --env-file .env ps stock-backend
