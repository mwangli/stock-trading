# AI_GENERATE_START ----------------------------------
from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path

# 当前真实交易只使用这一组代码内固定参数，不从环境变量读取。
FIXED_TIME_URGENCY_STRATEGY = "FIXED_TIME_URGENCY"
TRADING_MIN_PRICE = 0.01
TRADING_MAX_PRICE = 1_000_000.0
TRADING_ORDER_FEE = 5.0
EXECUTION_WINDOW_SECONDS = 15 * 60
ORDER_WAIT_SECONDS = 60
ORDER_POLL_SECONDS = 5
CANCEL_CONFIRM_SECONDS = 30
MAX_ATTEMPTS = 12
PASSIVE_FRACTION = 0.50
NORMAL_FRACTION = 0.85
BACKTEST_CAPITAL_SCENARIOS = (1_000, 2_000, 3_000, 5_000, 10_000)


@dataclass(frozen=True)
class AppConfig:
    """Runtime paths and optional network settings."""

    project_dir: Path
    data_dir: Path
    qlib_dir: Path
    artifact_dir: Path
    state_db_path: Path
    baostock_proxy: str | None
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
    trading_order_fee: float
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
    backtest_capital_scenarios: tuple[int, ...]
    model_evaluation_observations: int
    model_max_drawdown: float
    model_min_total_gross_return: float
    model_feature_batch_days: int = 30

    @classmethod
    def from_env(cls) -> "AppConfig":
        source_project_dir = Path(__file__).resolve().parents[2]
        project_dir = (
            source_project_dir
            if os.name == "nt"
            else Path.home() / "stock-trading"
        )
        _load_env_file(project_dir / ".env")
        runtime_dir = project_dir / "data"
        data_dir = runtime_dir / "market"
        qlib_dir = runtime_dir / "qlib"
        artifact_dir = runtime_dir / "artifacts"
        state_db_path = runtime_dir / "state" / "trading.db"
        proxy = os.getenv("BAOSTOCK_PROXY")
        broker_token = os.getenv("ZXBROKER_TOKEN")
        return cls(
            project_dir=project_dir,
            data_dir=data_dir,
            qlib_dir=qlib_dir,
            artifact_dir=artifact_dir,
            state_db_path=state_db_path,
            baostock_proxy=proxy or None,
            market_history_days=min(
                365 * 5,
                max(365, int(os.getenv("STOCK_TRADING_HISTORY_DAYS", str(365 * 3)))),
            ),
            bark_device_key=os.getenv("DEVICE_KEY", "").strip(),
            broker_enabled=True,
            model_feature_batch_days=max(
                1, int(os.getenv("MODEL_FEATURE_BATCH_DAYS", "30"))
            ),
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
            trading_order_fee=TRADING_ORDER_FEE,
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
            backtest_capital_scenarios=BACKTEST_CAPITAL_SCENARIOS,
            model_evaluation_observations=max(
                2,
                int(os.getenv("MODEL_EVALUATION_OBSERVATIONS", "20")),
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
# AI_GENERATE_END ----------------------------------
