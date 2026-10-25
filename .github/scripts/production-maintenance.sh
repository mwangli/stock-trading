#!/usr/bin/env bash
set -Eeuo pipefail

operation="${MAINTENANCE_OPERATION:-inspect}"
deploy_dir="${DEPLOY_DIR:?DEPLOY_DIR is required}"
image_prune_until="${DOCKER_IMAGE_PRUNE_UNTIL:-168h}"
builder_prune_until="${DOCKER_BUILDER_PRUNE_UNTIL:-24h}"
history_retention_days="${HISTORY_RETENTION_DAYS:-30}"
journal_retention_days="${JOURNAL_RETENTION_DAYS:-14}"

require_days() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "$name must be a positive integer: $value" >&2
    exit 1
  fi
}

require_duration() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*[smh]$ ]]; then
    echo "$name must be a positive Docker duration such as 24h: $value" >&2
    exit 1
  fi
}

safe_remove_directory() {
  local path="$1"
  local allowed_root="$2"
  local container
  local resolved_path
  local resolved_root
  local resolved_source
  local source

  resolved_path="$(realpath -m "$path")"
  resolved_root="$(realpath -m "$allowed_root")"
  if [[ "$resolved_path" != "$resolved_root/"* ]]; then
    echo "Refusing to remove path outside $resolved_root: $resolved_path" >&2
    exit 1
  fi
  while IFS= read -r container; do
    while IFS= read -r source; do
      [[ -n "$source" ]] || continue
      resolved_source="$(realpath -m "$source")"
      if [[ "$resolved_source" == "$resolved_path" \
        || "$resolved_source" == "$resolved_path/"* \
        || "$resolved_path" == "$resolved_source/"* ]]; then
        echo "Refusing to remove container-mounted path: $resolved_path" >&2
        exit 1
      fi
    done < <(docker inspect --format '{{range .Mounts}}{{println .Source}}{{end}}' "$container")
  done < <(docker ps -q)
  rm -rf -- "$resolved_path"
}

show_runtime_tmp() {
  local container
  for container in stock-backend stock-agent; do
    if docker inspect "$container" >/dev/null 2>&1; then
      printf '%s ' "$container"
      docker exec "$container" du -sh /app/.tmp 2>/dev/null || echo "runtime tmp unavailable"
    fi
  done
}

show_containers() {
  docker ps -a --filter 'name=stock-' \
    --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
}

verify_production_health() {
  local container
  local state
  local health
  local expected_containers=(
    stock-frontend
    stock-backend
    stock-agent
    stock-mysql
    stock-mongo
    stock-redis
  )

  for container in "${expected_containers[@]}"; do
    state="$(docker inspect --format '{{.State.Status}}' "$container")"
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container")"
    if [[ "$state" != "running" || "$health" != "healthy" ]]; then
      echo "Production container is not healthy: $container state=$state health=$health" >&2
      return 1
    fi
  done
  curl --fail --silent --show-error http://127.0.0.1/healthz >/dev/null
}

show_storage() {
  echo "=== FILESYSTEM ==="
  df -h / "$deploy_dir"
  echo "=== DOCKER ==="
  docker system df
  echo "=== CONTAINERS ==="
  show_containers
  echo "=== DEPLOY DIRECTORY ==="
  du -xhd1 "$deploy_dir" 2>/dev/null | sort -h
  echo "=== SYSTEM LOGS ==="
  journalctl --disk-usage
  du -xhd1 /var/log 2>/dev/null | sort -h
  echo "=== SYSTEM CACHE ==="
  du -xhd1 /var/cache /tmp /var/tmp 2>/dev/null | sort -h
  echo "=== RUNTIME TMP ==="
  show_runtime_tmp
}

prune_docker_storage() {
  echo "Cleaning unused Docker images older than $image_prune_until."
  docker image prune --all --force --filter "until=$image_prune_until"
  echo "Cleaning Docker build cache older than $builder_prune_until."
  docker builder prune --all --force --filter "until=$builder_prune_until"
}

verify_latest_backup() {
  local backup_dir="$1"
  local checksum_file
  local latest_backup

  [[ -d "$backup_dir" ]] || return 0
  latest_backup="$(find "$backup_dir" -mindepth 1 -maxdepth 1 -type f ! -name '*.sha256' \
    -printf '%T@ %p\n' | sort -nr | head -n 1 | cut -d' ' -f2-)"
  if [[ -z "$latest_backup" ]]; then
    echo "No backup artifact found in $backup_dir" >&2
    exit 1
  fi
  checksum_file="$latest_backup.sha256"
  if [[ ! -f "$checksum_file" ]]; then
    echo "Backup checksum is missing: $checksum_file" >&2
    exit 1
  fi
  (
    cd "$backup_dir"
    sha256sum --check "$(basename "$checksum_file")"
  )
}

prune_history() {
  local root="$1"
  local entry_type="$2"
  local label="$3"
  local keep_latest="${4:-false}"
  local index
  local entry
  local -a entries=()

  [[ -d "$root" ]] || return 0
  mapfile -t entries < <(
    find "$root" -mindepth 1 -maxdepth 1 -type "$entry_type" \
      ! -name '*.sha256' \
      -printf '%T@ %p\n' | sort -nr | cut -d' ' -f2-
  )
  for ((index = 0; index < ${#entries[@]}; index++)); do
    [[ "$keep_latest" == "true" && "$index" -eq 0 ]] && continue
    entry="${entries[$index]}"
    if [[ -n "$(find "$entry" -maxdepth 0 -mtime "+$history_retention_days" -print -quit)" ]]; then
      if [[ "$entry_type" == "d" ]]; then
        safe_remove_directory "$entry" "$root"
      else
        rm -f -- "$entry" "$entry.sha256"
      fi
      echo "Removed expired $label: $entry"
    fi
  done
}

prune_server_storage() {
  verify_latest_backup "$deploy_dir/backups/mysql"
  verify_latest_backup "$deploy_dir/backups/mongo"
  prune_history "$deploy_dir/backups/mysql" f "MySQL backup" true
  prune_history "$deploy_dir/backups/mongo" f "Mongo backup" true
  prune_history "$deploy_dir/backups/deploy-config" d "configuration backup" true
  prune_history "$deploy_dir/migration" d "migration directory"
  prune_history "$deploy_dir/repair-source" d "repair source directory"

  journalctl --vacuum-time="${journal_retention_days}d" --vacuum-size=256M
  if command -v logrotate >/dev/null 2>&1; then
    logrotate /etc/logrotate.conf
  fi
  if command -v apt-get >/dev/null 2>&1; then
    apt-get clean
  fi
  if command -v systemd-tmpfiles >/dev/null 2>&1; then
    systemd-tmpfiles --clean
  fi
}

require_duration DOCKER_IMAGE_PRUNE_UNTIL "$image_prune_until"
require_duration DOCKER_BUILDER_PRUNE_UNTIL "$builder_prune_until"
require_days HISTORY_RETENTION_DAYS "$history_retention_days"
require_days JOURNAL_RETENTION_DAYS "$journal_retention_days"

case "$operation" in
  inspect)
    show_storage
    verify_production_health
    ;;
  deploy)
    echo "=== DOCKER BEFORE CLEANUP ==="
    docker system df
    prune_docker_storage
    echo "=== DOCKER AFTER CLEANUP ==="
    docker system df
    ;;
  cleanup)
    verify_production_health
    echo "=== STORAGE BEFORE CLEANUP ==="
    show_storage
    prune_docker_storage
    prune_server_storage
    echo "=== STORAGE AFTER CLEANUP ==="
    show_storage
    verify_production_health
    ;;
  *)
    echo "Unsupported maintenance operation: $operation" >&2
    exit 1
    ;;
esac
