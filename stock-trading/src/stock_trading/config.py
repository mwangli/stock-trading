# AI_GENERATE_START ------------
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
    trading_min_price: float
    trading_max_price: float
    trading_min_commission: float
    trading_commission_rate: float
    trading_transfer_fee_rate: float
    trading_stamp_duty_rate: float
    trading_allowed_prefixes: tuple[str, ...]
    trading_max_prediction_age_days: int
    trading_order_poll_attempts: int
    trading_order_poll_interval_seconds: float
    model_evaluation_window_days: int
    model_max_drawdown: float
    model_min_total_gross_return: float

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
            trading_min_price=max(
                0.01, float(os.getenv("STOCK_TRADING_MIN_PRICE", "5"))
            ),
            trading_max_price=max(
                0.01, float(os.getenv("STOCK_TRADING_MAX_PRICE", "20"))
            ),
            trading_min_commission=max(
                0.0, float(os.getenv("STOCK_TRADING_MIN_COMMISSION", "5"))
            ),
            trading_commission_rate=max(
                0.0, float(os.getenv("STOCK_TRADING_COMMISSION_RATE", "0.0003"))
            ),
            trading_transfer_fee_rate=max(
                0.0, float(os.getenv("STOCK_TRADING_TRANSFER_FEE_RATE", "0.00001"))
            ),
            trading_stamp_duty_rate=max(
                0.0, float(os.getenv("STOCK_TRADING_STAMP_DUTY_RATE", "0.0005"))
            ),
            trading_allowed_prefixes=tuple(
                value.strip().upper()
                for value in os.getenv(
                    "STOCK_TRADING_ALLOWED_PREFIXES", "SH60,SZ00"
                ).split(",")
                if value.strip()
            ),
            trading_max_prediction_age_days=max(
                1, int(os.getenv("STOCK_TRADING_MAX_PREDICTION_AGE_DAYS", "7"))
            ),
            trading_order_poll_attempts=max(
                1, int(os.getenv("STOCK_TRADING_ORDER_POLL_ATTEMPTS", "30"))
            ),
            trading_order_poll_interval_seconds=max(
                0.0,
                float(os.getenv("STOCK_TRADING_ORDER_POLL_INTERVAL_SECONDS", "2")),
            ),
            model_evaluation_window_days=max(
                2, int(os.getenv("MODEL_EVALUATION_WINDOW_DAYS", "20"))
            ),
            model_max_drawdown=min(
                1.0, max(0.0, float(os.getenv("MODEL_MAX_DRAWDOWN", "0.10")))
            ),
            model_min_total_gross_return=float(
                os.getenv("MODEL_MIN_TOTAL_GROSS_RETURN", "0")
            ),
        )


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}
# AI_GENERATE_END ------------
