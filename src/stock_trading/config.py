# AI_GENERATE_START -----------------------
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path

# 当前真实交易只使用这一组代码内固定参数，不从环境变量读取。
FIXED_TIME_URGENCY_STRATEGY = "FIXED_TIME_URGENCY"
TRADING_MIN_PRICE = 0.01
TRADING_MAX_PRICE = 1_000_000.0
EXECUTION_WINDOW_SECONDS = 15 * 60
ORDER_WAIT_SECONDS = 60
ORDER_POLL_SECONDS = 5
CANCEL_CONFIRM_SECONDS = 30
MAX_ATTEMPTS = 12
PASSIVE_FRACTION = 0.50
NORMAL_FRACTION = 0.85


@dataclass(frozen=True)
class AppConfig:
    """Runtime paths and optional network settings."""

    project_dir: Path
    data_dir: Path
    qlib_dir: Path
    artifact_dir: Path
    state_db_path: Path
    baostock_proxy: str | None
    baostock_workers: int
    market_history_days: int
    bark_device_key: str
    broker_enabled: bool
    broker_mobile_code: str
    broker_account: str
    broker_encoded_password: str
    broker_sign_key: str
    broker_intact_to_server: str
    broker_token: str | None
    broker_timeout_seconds: float
    broker_token_expire_minutes: int
    broker_captcha_auto_submit_enabled: bool
    broker_ocr_timeout_seconds: float
    broker_ocr_request_interval_seconds: float
    broker_baidu_ocr_api_key: str
    broker_baidu_ocr_secret_key: str
    broker_captcha_max_retries: int
    broker_login_failure_cooldown_seconds: int
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
    trading_execution_strategy: str
    trading_execution_window_seconds: int
    trading_order_wait_seconds: int
    trading_order_poll_seconds: int
    trading_cancel_confirm_seconds: int
    trading_max_attempts: int
    trading_passive_fraction: float
    trading_normal_fraction: float
    model_evaluation_window_days: int
    model_max_drawdown: float
    model_min_total_gross_return: float

    @classmethod
    def from_env(cls) -> "AppConfig":
        project_dir = Path(__file__).resolve().parents[2]
        _load_env_file(project_dir / ".env")
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
            baostock_workers=min(4, max(1, int(os.getenv("BAOSTOCK_WORKERS", "2")))),
            market_history_days=min(
                365 * 5,
                max(365, int(os.getenv("STOCK_TRADING_HISTORY_DAYS", str(365 * 3)))),
            ),
            bark_device_key=os.getenv("DEVICE_KEY", "").strip(),
            broker_enabled=True,
            broker_mobile_code=os.getenv("ZXBROKER_MOBILE_CODE", ""),
            broker_account=os.getenv("ZXBROKER_ACCOUNT", ""),
            broker_encoded_password=os.getenv("ZXBROKER_ENCODED_PASSWORD", ""),
            broker_sign_key=os.getenv("ZXBROKER_SIGN_KEY", ""),
            broker_intact_to_server=os.getenv("ZXBROKER_INTACT_TO_SERVER", ""),
            broker_token=broker_token or None,
            broker_timeout_seconds=max(1.0, float(os.getenv("ZXBROKER_TIMEOUT_SECONDS", "20"))),
            broker_token_expire_minutes=30,
            broker_captcha_auto_submit_enabled=True,
            broker_ocr_timeout_seconds=max(
                1.0, float(os.getenv("ZXBROKER_OCR_TIMEOUT_SECONDS", "20"))
            ),
            broker_ocr_request_interval_seconds=max(
                0.0,
                float(os.getenv("ZXBROKER_OCR_REQUEST_INTERVAL_SECONDS", "1.05")),
            ),
            broker_baidu_ocr_api_key=os.getenv("BAIDU_OCR_API_KEY", ""),
            broker_baidu_ocr_secret_key=os.getenv("BAIDU_OCR_SECRET_KEY", ""),
            broker_captcha_max_retries=min(
                3, max(1, int(os.getenv("ZXBROKER_CAPTCHA_MAX_RETRIES", "3")))
            ),
            broker_login_failure_cooldown_seconds=max(
                1,
                int(os.getenv("ZXBROKER_LOGIN_FAILURE_COOLDOWN_SECONDS", "30")),
            ),
            broker_max_order_count=max(1, int(os.getenv("ZXBROKER_MAX_ORDER_COUNT", "100"))),
            broker_history_page_size=max(1, int(os.getenv("ZXBROKER_HISTORY_PAGE_SIZE", "500"))),
            broker_history_max_records=max(
                1, int(os.getenv("ZXBROKER_HISTORY_MAX_RECORDS", "5000"))
            ),
            broker_history_request_interval_seconds=max(
                0.0, float(os.getenv("ZXBROKER_HISTORY_REQUEST_INTERVAL_SECONDS", "0.2"))
            ),
            trading_min_price=TRADING_MIN_PRICE,
            trading_max_price=TRADING_MAX_PRICE,
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
            trading_execution_strategy=FIXED_TIME_URGENCY_STRATEGY,
            trading_execution_window_seconds=EXECUTION_WINDOW_SECONDS,
            trading_order_wait_seconds=ORDER_WAIT_SECONDS,
            trading_order_poll_seconds=ORDER_POLL_SECONDS,
            trading_cancel_confirm_seconds=CANCEL_CONFIRM_SECONDS,
            trading_max_attempts=MAX_ATTEMPTS,
            trading_passive_fraction=PASSIVE_FRACTION,
            trading_normal_fraction=NORMAL_FRACTION,
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
# AI_GENERATE_END -----------------------
