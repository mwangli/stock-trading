#!/usr/bin/env bash
set -euo pipefail

install_scheduler() {
  local project_dir="${1:?}"
  local release_dir="${2:?}"
  local system_path="${3:?}"
  local config_dir="$project_dir/config"
  local cron_file="$config_dir/stock-trading.cron"
  local scheduler="$config_dir/scheduler.sh"
  local logrotate_file="$config_dir/stock-trading.logrotate"
  local legacy_cron_dir="$project_dir/cron"
  local cron_template="$release_dir/config/stock-trading.cron"
  local scheduler_source="$release_dir/script/scheduler.sh"
  local logrotate_template="$release_dir/config/stock-trading.logrotate"
  local tmp_dir desired_cron current_cron next_cron installed_cron desired_logrotate

  mkdir -p "$config_dir"
  if [ ! -x "$scheduler" ] || ! cmp -s "$scheduler_source" "$scheduler"; then
    install -m 0755 "$scheduler_source" "$scheduler"
  fi

  tmp_dir="$(mktemp -d "$config_dir/.install.XXXXXX")"
  trap "rm -rf -- '$tmp_dir'" EXIT
  desired_cron="$tmp_dir/desired.cron"
  current_cron="$tmp_dir/current.cron"
  next_cron="$tmp_dir/next.cron"
  installed_cron="$tmp_dir/installed.cron"
  desired_logrotate="$tmp_dir/desired.logrotate"

  sed \
    -e "s|__PROJECT_DIR__|$project_dir|g" \
    -e "s|__SYSTEM_PATH__|$system_path|g" \
    "$cron_template" > "$desired_cron"
  sed "s|__PROJECT_DIR__|$project_dir|g" "$logrotate_template" > "$desired_logrotate"

  crontab -l > "$current_cron" 2>/dev/null || true
  extract_managed_cron "$current_cron" > "$installed_cron"
  if ! cmp -s "$desired_cron" "$installed_cron"; then
    awk '
      $0 == "# STOCK_TRADING_CRON_BEGIN" { managed = 1; next }
      $0 == "# STOCK_TRADING_CRON_END" { managed = 0; next }
      !managed { print }
    ' "$current_cron" > "$next_cron"
    cat "$desired_cron" >> "$next_cron"
    crontab "$next_cron"
    crontab -l | extract_managed_cron > "$installed_cron"
    cmp "$desired_cron" "$installed_cron"
  fi

  cmp -s "$desired_cron" "$cron_file" || install -m 0644 "$desired_cron" "$cron_file"
  cmp -s "$desired_logrotate" "$logrotate_file" || install -m 0644 "$desired_logrotate" "$logrotate_file"
  rm -f \
    "$legacy_cron_dir/scheduler.sh" \
    "$legacy_cron_dir/stock-trading.cron" \
    "$legacy_cron_dir/stock-trading.logrotate" \
    "$legacy_cron_dir/run-cron.sh"
  rmdir "$legacy_cron_dir" 2>/dev/null || true
}

extract_managed_cron() {
  awk '
    $0 == "# STOCK_TRADING_CRON_BEGIN" { managed = 1 }
    managed { print }
    $0 == "# STOCK_TRADING_CRON_END" { exit }
  ' "$@"
}

run_scheduled() {
  exec > /dev/null 2>> "${LOG:?}"
  exec 9> "${LOCK:?}"
  flock -n 9

  cd "${PROJECT_DIR:?}"
  local command_spec command_name
  local -a command_parts
  for command_spec in "$@"; do
    read -r -a command_parts <<< "$command_spec"
    command_name="${command_parts[0]:?}"
    "$PROJECT_DIR/venv/bin/$command_name" "${command_parts[@]:1}"
  done
}

mode="${1:-}"
shift || true
case "$mode" in
  install) install_scheduler "$@" ;;
  run) run_scheduled "$@" ;;
  *) echo "Usage: $0 install PROJECT_DIR RELEASE_DIR SYSTEM_PATH | run COMMAND..." >&2; exit 2 ;;
esac
