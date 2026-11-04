# AI_GENERATE_START --
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class AgentConfig:
    """Runtime configuration for the standalone Agent package."""

    agent_home: Path
    memory_db_path: Path
    trading_home: Path
    trading_data_dir: Path
    state_db_path: Path
    deepseek_api_key: str
    deepseek_base_url: str
    deepseek_model: str
    request_timeout_seconds: float
    max_tool_rounds: int
    max_query_rows: int
    github_token: str
    github_repository: str
    github_target_branch: str
    github_api_url: str
    github_workflow: str
    max_repair_rounds: int
    repair_poll_seconds: float
    repair_timeout_seconds: float
    max_repair_files: int
    max_repair_file_bytes: int

    @classmethod
    def from_env(cls) -> "AgentConfig":
        cwd = Path.cwd().resolve()
        default_agent_home = (
            cwd / "agent"
            if (cwd / "agent" / "pyproject.toml").exists()
            else Path.home() / "stock-agent"
        )
        agent_home = Path(
            os.getenv("STOCK_AGENT_HOME", str(default_agent_home))
        ).expanduser().resolve()
        _load_env_file(agent_home / ".env")

        default_trading_home = (
            cwd if (cwd / "data" / "state").exists() else Path.home() / "stock-trading"
        )
        trading_home = Path(
            os.getenv("STOCK_TRADING_HOME", str(default_trading_home))
        ).expanduser().resolve()
        trading_data_dir = Path(
            os.getenv("STOCK_TRADING_DATA_DIR", str(trading_home / "data"))
        ).expanduser().resolve()
        memory_db_path = Path(
            os.getenv(
                "STOCK_AGENT_MEMORY_DB",
                str(agent_home / "data" / "agent.db"),
            )
        ).expanduser().resolve()
        return cls(
            agent_home=agent_home,
            memory_db_path=memory_db_path,
            trading_home=trading_home,
            trading_data_dir=trading_data_dir,
            state_db_path=trading_data_dir / "state" / "trading.db",
            deepseek_api_key=os.getenv("DEEPSEEK_API_KEY", "").strip(),
            deepseek_base_url=os.getenv(
                "DEEPSEEK_BASE_URL", "https://api.deepseek.com"
            ).strip(),
            deepseek_model=os.getenv("DEEPSEEK_MODEL", "deepseek-flash").strip(),
            request_timeout_seconds=max(
                10.0, float(os.getenv("STOCK_AGENT_TIMEOUT_SECONDS", "120"))
            ),
            max_tool_rounds=min(
                12, max(1, int(os.getenv("STOCK_AGENT_MAX_TOOL_ROUNDS", "6")))
            ),
            max_query_rows=min(
                1000, max(10, int(os.getenv("STOCK_AGENT_MAX_QUERY_ROWS", "200")))
            ),
            github_token=(
                os.getenv("STOCK_AGENT_GITHUB_TOKEN")
                or os.getenv("GITHUB_TOKEN", "")
            ).strip(),
            github_repository=os.getenv(
                "STOCK_AGENT_GITHUB_REPOSITORY", "mwangli/stock-trading"
            ).strip(),
            github_target_branch=os.getenv(
                "STOCK_AGENT_GITHUB_TARGET_BRANCH", "stock-agent"
            ).strip(),
            github_api_url=os.getenv(
                "STOCK_AGENT_GITHUB_API_URL", "https://api.github.com"
            ).rstrip("/"),
            github_workflow=os.getenv(
                "STOCK_AGENT_GITHUB_WORKFLOW", "agent-ci.yml"
            ).strip(),
            max_repair_rounds=min(
                2, max(1, int(os.getenv("STOCK_AGENT_MAX_REPAIR_ROUNDS", "2")))
            ),
            repair_poll_seconds=max(
                2.0, float(os.getenv("STOCK_AGENT_REPAIR_POLL_SECONDS", "10"))
            ),
            repair_timeout_seconds=max(
                60.0, float(os.getenv("STOCK_AGENT_REPAIR_TIMEOUT_SECONDS", "1800"))
            ),
            max_repair_files=min(
                20, max(1, int(os.getenv("STOCK_AGENT_MAX_REPAIR_FILES", "20")))
            ),
            max_repair_file_bytes=min(
                500_000,
                max(1024, int(os.getenv("STOCK_AGENT_MAX_REPAIR_FILE_BYTES", "163840"))),
            ),
        )

    def ensure_runtime_dirs(self) -> None:
        self.memory_db_path.parent.mkdir(parents=True, exist_ok=True)
        if os.name != "nt":
            self.memory_db_path.parent.chmod(0o700)


def _load_env_file(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].strip()
        key, separator, value = line.partition("=")
        key = key.strip()
        if not separator or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        os.environ.setdefault(key, value)
# AI_GENERATE_END --