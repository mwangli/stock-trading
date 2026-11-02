#!/usr/bin/env bash
set -euo pipefail

release_tag="${1:-}"
if [[ ! "$release_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid release tag: $release_tag" >&2
  exit 2
fi

project_dir="$HOME/stock-trading"
releases_dir="$project_dir/releases"
release_dir="$releases_dir/$release_tag"
venv_dir="$project_dir/venv"
python_dir="$project_dir/python"
uv_cache_dir="$project_dir/.uv-cache"
env_file="$project_dir/.env"
data_dir="$project_dir/data"
logs_dir="$project_dir/logs"
cron_dir="$project_dir/cron"
cron_file="$cron_dir/stock-trading.cron"
logrotate_file="$cron_dir/stock-trading.logrotate"
cron_template="$release_dir/config/stock-trading.cron"
logrotate_template="$release_dir/config/stock-trading.logrotate"
bootstrap_script="$release_dir/script/bootstrap-server.sh"
commands="stock-help stock-train stock-collect stock-rank stock-sell stock-buy stock-broker stock-perform stock-evaluate stock-clean"
system_path="$HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

for required_file in "$bootstrap_script" "$cron_template" "$logrotate_template"; do
  if [ ! -f "$required_file" ]; then
    echo "Missing release file: $required_file" >&2
    exit 1
  fi
done

if ! STOCK_TRADING_PROJECT_DIR="$project_dir" bash "$bootstrap_script" --check; then
  echo "Server bootstrap is incomplete." >&2
  echo "Run: bash $bootstrap_script --apply" >&2
  exit 1
fi

if [ ! -s "$env_file" ] || [ ! -r "$env_file" ]; then
  echo "Missing or unreadable project environment file: $env_file" >&2
  exit 1
fi
env_owner="$(stat -c '%U' "$env_file")"
env_mode="$(stat -c '%a' "$env_file")"
if [ "$env_owner" != "$(id -un)" ]; then
  echo "Environment file must be owned by $(id -un): $env_file" >&2
  exit 1
fi
case "$env_mode" in
  400|600) ;;
  *)
    echo "Environment file permissions must be 400 or 600: $env_file" >&2
    exit 1
    ;;
esac

set -- "$release_dir"/*.whl
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Expected exactly one wheel in $release_dir" >&2
  exit 1
fi
wheel="$1"

mkdir -p \
  "$python_dir" \
  "$uv_cache_dir" \
  "$data_dir/market" \
  "$data_dir/qlib" \
  "$data_dir/artifacts" \
  "$data_dir/state" \
  "$cron_dir" \
  "$logs_dir"

export UV_DEFAULT_INDEX="https://mirrors.aliyun.com/pypi/simple/"
export UV_HTTP_TIMEOUT=300
export UV_HTTP_RETRIES=5
export UV_PYTHON_INSTALL_DIR="$python_dir"
export UV_CACHE_DIR="$uv_cache_dir"
export PATH="$system_path"

uv python install 3.12
python_version=""
if [ -x "$venv_dir/bin/python" ]; then
  python_version="$("$venv_dir/bin/python" -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")' 2>/dev/null || true)"
fi
if [ "$python_version" != "3.12" ]; then
  if [ -d "$venv_dir" ]; then
    uv venv --clear --managed-python --python 3.12 "$venv_dir"
  else
    uv venv --managed-python --python 3.12 "$venv_dir"
  fi
fi
uv pip install --python "$venv_dir/bin/python" "$wheel"

installed_version="$("$venv_dir/bin/python" -c 'import importlib.metadata; print(importlib.metadata.version("stock-trading"))')"
test "$release_tag" = "v$installed_version"
configured_project_dir="$(env -i \
  HOME="$HOME" \
  PATH="$venv_dir/bin:$system_path" \
  LANG="${LANG:-C.UTF-8}" \
  TZ="Asia/Shanghai" \
  "$venv_dir/bin/python" -c 'from stock_trading.config import AppConfig; print(AppConfig.from_env().data_dir.parent.parent)')"
test "$configured_project_dir" = "$project_dir"
for command_name in $commands; do
  test -x "$venv_dir/bin/$command_name"
done

touch "$logs_dir/stock-trading.log" "$data_dir/state/scheduler.lock"

desired_cron="$cron_dir/.desired.cron.$$"
current_cron="$cron_dir/.current.cron.$$"
next_cron="$cron_dir/.next.cron.$$"
installed_cron="$cron_dir/.installed.cron.$$"
desired_logrotate="$cron_dir/.desired.logrotate.$$"
trap 'rm -f "$desired_cron" "$current_cron" "$next_cron" "$installed_cron" "$desired_logrotate"' EXIT

sed \
  -e "s|__PROJECT_DIR__|$project_dir|g" \
  -e "s|__SYSTEM_PATH__|$system_path|g" \
  "$cron_template" \
  > "$desired_cron"
sed "s|__PROJECT_DIR__|$project_dir|g" "$logrotate_template" > "$desired_logrotate"

crontab -l > "$current_cron" 2>/dev/null || true
awk '
  $0 == "# STOCK_TRADING_CRON_BEGIN" { managed = 1 }
  managed { print }
  $0 == "# STOCK_TRADING_CRON_END" { exit }
' "$current_cron" > "$installed_cron"

if ! cmp -s "$desired_cron" "$installed_cron"; then
  awk '
    $0 == "# STOCK_TRADING_CRON_BEGIN" { managed = 1; next }
    $0 == "# STOCK_TRADING_CRON_END" { managed = 0; next }
    !managed { print }
  ' "$current_cron" > "$next_cron"
  cat "$desired_cron" >> "$next_cron"
  crontab "$next_cron"
  crontab -l | awk '
    $0 == "# STOCK_TRADING_CRON_BEGIN" { managed = 1 }
    managed { print }
    $0 == "# STOCK_TRADING_CRON_END" { exit }
  ' > "$installed_cron"
  cmp "$desired_cron" "$installed_cron"
fi

if ! cmp -s "$desired_cron" "$cron_file"; then
  install -m 0644 "$desired_cron" "$cron_file"
fi
if ! cmp -s "$desired_logrotate" "$logrotate_file"; then
  install -m 0644 "$desired_logrotate" "$logrotate_file"
fi

find "$releases_dir" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -name 'v*.*.*' \
  -printf '%f\n' \
  | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/' \
  | sort -Vr \
  | awk 'NR > 3' \
  | while IFS= read -r old_tag; do
      if [[ "$old_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        old_release="$releases_dir/$old_tag"
        if [ "$old_release" != "$release_dir" ]; then
          rm -rf -- "$old_release"
        fi
      fi
    done

printf 'Release %s installed.\n' "$release_tag"
