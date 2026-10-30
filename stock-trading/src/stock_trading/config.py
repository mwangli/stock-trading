# AI_GENERATE_START ---
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class AppConfig:
    """Runtime paths and optional network settings."""

    project_dir: Path
    data_dir: Path
    qlib_dir: Path
    artifact_dir: Path
    state_db_path: Path
    baostock_proxy: str | None

    @classmethod
    def from_env(cls) -> "AppConfig":
        project_dir = Path(__file__).resolve().parents[2]
        data_dir = Path(os.getenv("STOCK_TRADING_DATA_DIR", project_dir / "var" / "market"))
        qlib_dir = Path(os.getenv("STOCK_TRADING_QLIB_DIR", project_dir / "var" / "qlib"))
        artifact_dir = Path(
            os.getenv("STOCK_TRADING_ARTIFACT_DIR", project_dir / "var" / "artifacts")
        )
        state_db_path = Path(
            os.getenv("STOCK_TRADING_STATE_DB", project_dir / "var" / "state" / "trading.db")
        )
        proxy = os.getenv("BAOSTOCK_PROXY")
        return cls(
            project_dir=project_dir,
            data_dir=data_dir,
            qlib_dir=qlib_dir,
            artifact_dir=artifact_dir,
            state_db_path=state_db_path,
            baostock_proxy=proxy or None,
        )
# AI_GENERATE_END ---
