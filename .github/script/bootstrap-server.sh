#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --check|--apply" >&2
}

mode="${1:---check}"
project_dir="${STOCK_TRADING_PROJECT_DIR:-$HOME/stock-trading}"
export PATH="$HOME/.local/bin:$PATH"
required_tools=(uv crontab flock logrotate setsid)

missing_tools() {
  local tool
  for tool in "${required_tools[@]}"; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      printf '%s\n' "$tool"
    fi
  done
  return 0
}

check_environment() {
  local missing
  missing="$(missing_tools)"
  if [ -n "$missing" ]; then
    echo "Missing server tools:" >&2
    printf '  %s\n' $missing >&2
    return 1
  fi
  if [ ! -d "$project_dir" ] || [ ! -w "$project_dir" ]; then
    echo "Project directory is missing or not writable: $project_dir" >&2
    return 1
  fi
}

run_as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    echo "Root privileges are required to install server packages." >&2
    return 1
  fi
}

install_system_tools() {
  if command -v apt-get >/dev/null 2>&1; then
    run_as_root apt-get update
    run_as_root apt-get install -y cron util-linux logrotate curl ca-certificates
  elif command -v dnf >/dev/null 2>&1; then
    run_as_root dnf install -y cronie util-linux logrotate curl ca-certificates
  elif command -v yum >/dev/null 2>&1; then
    run_as_root yum install -y cronie util-linux logrotate curl ca-certificates
  else
    echo "Unsupported package manager. Install cron, util-linux and logrotate manually." >&2
    return 1
  fi
}

apply_environment() {
  local system_tool
  local install_packages=0
  for system_tool in crontab flock logrotate setsid; do
    if ! command -v "$system_tool" >/dev/null 2>&1; then
      install_packages=1
    fi
  done
  if ! command -v curl >/dev/null 2>&1; then
    install_packages=1
  fi
  if [ "$install_packages" -eq 1 ]; then
    install_system_tools
  fi
  if ! command -v uv >/dev/null 2>&1; then
    curl -LsSf https://astral.sh/uv/install.sh | sh
    hash -r
  fi
  mkdir -p \
    "$project_dir/releases" \
    "$project_dir/python" \
    "$project_dir/.uv-cache" \
    "$project_dir/data/market" \
    "$project_dir/data/qlib" \
    "$project_dir/data/artifacts" \
    "$project_dir/data/state" \
    "$project_dir/cron" \
    "$project_dir/logs"
  check_environment
}

case "$mode" in
  --check)
    check_environment
    ;;
  --apply)
    apply_environment
    ;;
  *)
    usage
    exit 2
    ;;
esac
