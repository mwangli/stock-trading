#!/usr/bin/env bash
set -Eeuo pipefail

service_name="${1:?service name is required}"
container_name="${2:?container name is required}"
image_variable="${3:?image variable is required}"
checksum_file="${4:-}"

cd "${DEPLOY_DIR:?DEPLOY_DIR is required}"
test -f .env
test -f docker-compose.yml
if [[ -n "$checksum_file" ]]; then
  test -f "$checksum_file"
  sha256sum --check "$checksum_file"
fi

docker_config="$(mktemp -d)"
export DOCKER_CONFIG="$docker_config"
cleanup_registry() {
  docker logout "$ACR_REGISTRY" >/dev/null 2>&1 || true
  rm -rf "$docker_config"
}
trap cleanup_registry EXIT

previous_image="$(docker inspect --format '{{.Config.Image}}' "$container_name" 2>/dev/null || true)"

rollback() {
  exit_code=$?
  trap - ERR
  echo "Deployment failed for $service_name; collecting logs."
  docker compose --env-file .env logs --tail=150 "$service_name" || true
  if [[ -n "$previous_image" ]]; then
    echo "Rolling back $service_name to its previous image."
    export "$image_variable=$previous_image"
    docker compose --env-file .env up -d --no-deps --force-recreate "$service_name" || true
  fi
  exit "$exit_code"
}
trap rollback INT TERM HUP ERR

printf '%s' "$ACR_PASSWORD" | docker login "$ACR_REGISTRY" \
  --username "$ACR_USERNAME" --password-stdin
docker pull "$DEPLOY_IMAGE"

export "$image_variable=$DEPLOY_IMAGE"
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d --no-deps --force-recreate "$service_name"

container_id="$(docker compose --env-file .env ps -q "$service_name")"
test -n "$container_id"

healthy=0
for _ in $(seq 1 36); do
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
  if [[ "$status" == "healthy" || "$status" == "running" ]]; then
    healthy=1
    break
  fi
  if [[ "$status" == "unhealthy" || "$status" == "exited" || "$status" == "dead" ]]; then
    break
  fi
  sleep 5
done

test "$healthy" -eq 1
trap - INT TERM HUP ERR
docker compose --env-file .env ps "$service_name"
