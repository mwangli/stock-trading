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
data_dir="$project_dir/data"
logs_dir="$data_dir/logs"
config_dir="$project_dir/config"
completion_dir="$config_dir/completion"
bootstrap_script="$release_dir/script/bootstrap-server.sh"
scheduler_script="$release_dir/script/scheduler.sh"
commands="stock"
system_path="$HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

for required_file in \
  "$bootstrap_script" \
  "$scheduler_script" \
  "$release_dir/script/stock-completion.bash" \
  "$release_dir/script/stock-completion.ps1" \
  "$release_dir/config/stock-trading.cron"; do
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

set -- "$release_dir"/*.whl
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Expected exactly one wheel in $release_dir" >&2
  exit 1
fi
wheel="$1"

mkdir -p "$python_dir" "$uv_cache_dir" "$data_dir"/{market,qlib,artifacts,state} \
  "$completion_dir" "$config_dir" "$logs_dir"

install -m 0644 \
  "$release_dir/script/stock-completion.bash" \
  "$completion_dir/stock-completion.bash"
install -m 0644 \
  "$release_dir/script/stock-completion.ps1" \
  "$completion_dir/stock-completion.ps1"

bashrc="$HOME/.bashrc"
completion_begin="# STOCK_TRADING_COMPLETION_BEGIN"
completion_end="# STOCK_TRADING_COMPLETION_END"
touch "$bashrc"
if ! grep -Fqx "$completion_begin" "$bashrc"; then
  {
    printf '\n%s\n' "$completion_begin"
    printf 'if [ -f "%s" ]; then\n' "$completion_dir/stock-completion.bash"
    printf '  source "%s"\n' "$completion_dir/stock-completion.bash"
    printf 'fi\n%s\n' "$completion_end"
  } >> "$bashrc"
fi

shopt -s nullglob
env_files=("$project_dir"/*.env)
if [ "${#env_files[@]}" -eq 0 ]; then
  echo "WARN: No environment files found in $project_dir/*.env" >&2
fi
for env_file in "${env_files[@]}"; do
  if [ ! -r "$env_file" ]; then
    echo "WARN: Environment file is unreadable: $env_file" >&2
  fi
done

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
configured_project_dir="$("$venv_dir/bin/python" -c 'from stock_trading.config import AppConfig; print(AppConfig.from_env().data_dir.parent.parent)')"
test "$configured_project_dir" = "$project_dir"
for command_name in $commands; do
  test -x "$venv_dir/bin/$command_name"
done

touch "$logs_dir/stock-trading.log" "$data_dir/state/scheduler.lock"
bash "$scheduler_script" install "$project_dir" "$release_dir" "$system_path"

find "$releases_dir" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -name 'v*.*.*' \
  -printf '%f\n' \
  | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/' \
  | sort -Vr \
  | tail -n +4 \
  | while IFS= read -r old_tag; do
      old_release="$releases_dir/$old_tag"
      if [ "$old_release" != "$release_dir" ]; then
        rm -rf -- "$old_release"
      fi
    done

printf 'Release %s installed.\n' "$release_tag"
