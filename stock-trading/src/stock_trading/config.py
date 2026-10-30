# AI_GENERATE_START ------
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
    broker_enabled: bool
    broker_mobile_code: str
    broker_account: str
    broker_encoded_password: str
    broker_sign_key: str
    broker_intact_to_server: str
    broker_token: str | None
    broker_timeout_seconds: float
    broker_token_expire_minutes: int
    broker_max_order_count: int
    broker_history_page_size: int
    broker_history_max_records: int
    broker_history_request_interval_seconds: float

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
        broker_token = os.getenv("ZXBROKER_TOKEN")
        return cls(
            project_dir=project_dir,
            data_dir=data_dir,
            qlib_dir=qlib_dir,
            artifact_dir=artifact_dir,
            state_db_path=state_db_path,
            baostock_proxy=proxy or None,
            broker_enabled=_env_bool("ZXBROKER_ENABLED", False),
            broker_mobile_code=os.getenv("ZXBROKER_MOBILE_CODE", ""),
            broker_account=os.getenv("ZXBROKER_ACCOUNT", ""),
            broker_encoded_password=os.getenv("ZXBROKER_ENCODED_PASSWORD", ""),
            broker_sign_key=os.getenv("ZXBROKER_SIGN_KEY", ""),
            broker_intact_to_server=os.getenv("ZXBROKER_INTACT_TO_SERVER", ""),
            broker_token=broker_token or None,
            broker_timeout_seconds=max(1.0, float(os.getenv("ZXBROKER_TIMEOUT_SECONDS", "20"))),
            broker_token_expire_minutes=max(
                1, int(os.getenv("ZXBROKER_TOKEN_EXPIRE_MINUTES", "30"))
            ),
            broker_max_order_count=max(1, int(os.getenv("ZXBROKER_MAX_ORDER_COUNT", "100"))),
            broker_history_page_size=max(1, int(os.getenv("ZXBROKER_HISTORY_PAGE_SIZE", "500"))),
            broker_history_max_records=max(
                1, int(os.getenv("ZXBROKER_HISTORY_MAX_RECORDS", "5000"))
            ),
            broker_history_request_interval_seconds=max(
                0.0, float(os.getenv("ZXBROKER_HISTORY_REQUEST_INTERVAL_SECONDS", "0.2"))
            ),
        )


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}
# AI_GENERATE_END ------
