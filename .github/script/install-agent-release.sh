#!/usr/bin/env bash
set -euo pipefail

release_tag="${1:-}"
if [[ ! "$release_tag" =~ ^agent-v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid Agent release tag: $release_tag" >&2
  exit 2
fi
if [ -z "${DEEPSEEK_API_KEY:-}" ]; then
  echo "DEEPSEEK_API_KEY is not configured." >&2
  exit 1
fi

project_dir="$HOME/stock-agent"
trading_dir="$HOME/stock-trading"
releases_dir="$project_dir/releases"
release_dir="$releases_dir/$release_tag"
venv_dir="$project_dir/venv"
python_dir="$project_dir/python"
uv_cache_dir="$project_dir/.uv-cache"
data_dir="$project_dir/data"
env_file="$project_dir/.env"
system_path="$HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

set -- "$release_dir"/*.whl
if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
  echo "Expected exactly one Agent wheel in $release_dir" >&2
  exit 1
fi
wheel="$1"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required on the server." >&2
  exit 1
fi
if [ ! -d "$trading_dir/data" ]; then
  echo "Trading data directory is missing: $trading_dir/data" >&2
  exit 1
fi

mkdir -p "$python_dir" "$uv_cache_dir" "$data_dir"
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
uv pip install --python "$venv_dir/bin/python" --reinstall "$wheel"

installed_version="$("$venv_dir/bin/python" -c 'import importlib.metadata; print(importlib.metadata.version("stock-trading-agent"))')"
test "$release_tag" = "agent-v$installed_version"
test -x "$venv_dir/bin/stock-agent"

umask 077
temporary_env="$project_dir/.env.$$"
trap 'rm -f "$temporary_env"' EXIT
{
  printf 'DEEPSEEK_API_KEY=%s\n' "$DEEPSEEK_API_KEY"
  printf 'DEEPSEEK_BASE_URL=https://api.deepseek.com\n'
  printf 'DEEPSEEK_MODEL=%s\n' "${DEEPSEEK_MODEL:-deepseek-flash}"
  printf 'STOCK_AGENT_HOME=%s\n' "$project_dir"
  printf 'STOCK_TRADING_HOME=%s\n' "$trading_dir"
  printf 'STOCK_TRADING_DATA_DIR=%s\n' "$trading_dir/data"
  printf 'STOCK_AGENT_MAX_TOOL_ROUNDS=6\n'
  printf 'STOCK_AGENT_MAX_QUERY_ROWS=200\n'
  printf 'STOCK_AGENT_TIMEOUT_SECONDS=120\n'
} > "$temporary_env"
install -m 0600 "$temporary_env" "$env_file"
rm -f "$temporary_env"
trap - EXIT
unset DEEPSEEK_API_KEY

find "$releases_dir" \
  -mindepth 1 \
  -maxdepth 1 \
  -type d \
  -name 'agent-v*.*.*' \
  -printf '%f\n' \
  | awk '/^agent-v[0-9]+\.[0-9]+\.[0-9]+$/' \
  | sort -Vr \
  | awk 'NR > 3' \
  | while IFS= read -r old_tag; do
      old_release="$releases_dir/$old_tag"
      if [ "$old_release" != "$release_dir" ]; then
        rm -rf -- "$old_release"
      fi
    done

printf 'Agent release %s installed.\n' "$release_tag"
