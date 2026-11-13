# AI_GENERATE_START-5--------- ---------------------------------------------------------
from __future__ import annotations

import logging
import os
import re
from dataclasses import dataclass
from datetime import time
from pathlib import Path

# 当前真实交易只使用这一组代码内固定参数，不从环境变量读取。
FIXED_TIME_URGENCY_STRATEGY = "FIXED_TIME_URGENCY"
TRADING_MIN_PRICE = 0.01
TRADING_MAX_PRICE = 1_000_000.0
TRADING_ORDER_FEE = 5.0
EXIT_POSITION = 5
TRADING_MAX_HOLDING_DAYS = 5
TRADING_LIMIT_DOWN_LOOKBACK_DAYS = 10
TRADING_MAX_RECENT_LIMIT_DOWNS = 2
ORDER_WAIT_SECONDS = 60
ORDER_POLL_SECONDS = 5
CANCEL_CONFIRM_SECONDS = 30
MAX_ATTEMPTS = 12
PASSIVE_FRACTION = 0.50
NORMAL_FRACTION = 0.85
DEFAULT_BROKER_BASE_URL = "https://weixin.citicsinfo.com/reqxml"
BACKTEST_CAPITAL = 2_000
MODEL_MIN_POSITIVE_MONTH_RATIO = 0.60
MODEL_TRAIN_SPLIT = (70, 15, 15)
LOGGER = logging.getLogger(__name__)
ENV_NAME_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
REQUIRED_ENV_NAMES = (
    "ZXBROKER_MOBILE_CODE",
    "ZXBROKER_ACCOUNT",
    "ZXBROKER_ENCODED_PASSWORD",
    "ZXBROKER_SIGN_KEY",
    "ZXBROKER_INTACT_TO_SERVER",
    "BAIDU_OCR_API_KEY",
    "BAIDU_OCR_SECRET_KEY",
)
_LOADED_ENV_ROOTS: set[Path] = set()

# 共享交易时间约定；不含数据访问或执行副作用。
TRADING_TIME_CONTRACT = "fixed-model-matrix-v1"
WINDOW_CONTRACT = "full-day-minute5-v3"
SELL_START = time(9, 30)
SELL_END = time(11, 30)
BUY_START = time(14, 0)
BUY_END = time(15, 0)


def execution_window_seconds(side: str) -> int:
    """返回单个方向完整连续竞价时段的执行预算秒数。"""
    if side not in {"BUY", "SELL"}:
        raise ValueError("side must be BUY or SELL")
    start, end = (BUY_START, BUY_END) if side == "BUY" else (SELL_START, SELL_END)
    return ((end.hour - start.hour) * 60 + end.minute - start.minute) * 60


@dataclass(frozen=True)
class AppConfig:
    """Runtime paths and optional network settings."""

    market_dir: Path
    prepared_dir: Path
    model_dir: Path
    backtest_dir: Path
    active_model_id: str
    state_db_path: Path
    baostock_proxy: str | None
    market_history_days: int
    bark_device_key: str
    broker_base_url: str
    broker_mobile_code: str
    broker_account: str
    broker_encoded_password: str
    broker_sign_key: str
    broker_intact_to_server: str
    broker_token: str | None
    broker_timeout_seconds: float
    broker_token_expire_minutes: int
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
    minute_market_workers: int
    minute_market_request_interval_seconds: float
    trading_min_price: float
    trading_max_price: float
    trading_order_fee: float
    exit_position: int
    trading_max_holding_days: int
    trading_limit_down_lookback_days: int
    trading_max_recent_limit_downs: int
    trading_allowed_prefixes: tuple[str, ...]
    trading_max_prediction_age_days: int
    trading_execution_strategy: str
    trading_order_wait_seconds: int
    trading_order_poll_seconds: int
    trading_cancel_confirm_seconds: int
    trading_max_attempts: int
    trading_passive_fraction: float
    trading_normal_fraction: float
    backtest_capital: int
    model_evaluation_observations: int
    model_max_drawdown: float
    model_min_total_gross_return: float
    model_min_positive_month_ratio: float
    data_quality_min_ratio: float
    model_feature_batch_days: int = 60

    @property
    def data_dir(self) -> Path:
        """兼容旧调用方，返回市场原始数据根目录。"""
        return self.market_dir

    @classmethod
    def from_env(cls, *, project_dir: Path | None = None) -> "AppConfig":
        """Read configuration, optionally reusing an already loaded project environment."""
        if project_dir is None:
            project_dir = load_project_environment()
        runtime_dir = project_dir / "data"
        market_dir = runtime_dir / "market"
        models_dir = runtime_dir / "models"
        prepared_dir = models_dir / "prepared"
        model_dir = models_dir / "trained"
        backtest_dir = models_dir / "backtests"
        state_db_path = runtime_dir / "state" / "trading.db"
        baostock_proxy = _optional_env("BAOSTOCK_PROXY")
        broker_token = _optional_env("ZXBROKER_TOKEN")
        return cls(
            market_dir=market_dir,
            prepared_dir=prepared_dir,
            model_dir=model_dir,
            backtest_dir=backtest_dir,
            active_model_id=(
                os.getenv("STOCK_TRADING_ACTIVE_MODEL", "L1_daily_LightGBM").strip()
                or "L1_daily_LightGBM"
            ),
            state_db_path=state_db_path,
            baostock_proxy=baostock_proxy,
            market_history_days=min(
                365 * 5,
                max(365, _env_int("STOCK_TRADING_HISTORY_DAYS", 365 * 3)),
            ),
            bark_device_key=os.getenv("DEVICE_KEY", "").strip(),
            broker_base_url=(
                os.getenv("ZXBROKER_BASE_URL", DEFAULT_BROKER_BASE_URL).strip()
                or DEFAULT_BROKER_BASE_URL
            ),
            model_feature_batch_days=max(
                1, _env_int("MODEL_FEATURE_BATCH_DAYS", 60)
            ),
            broker_mobile_code=os.getenv("ZXBROKER_MOBILE_CODE", ""),
            broker_account=os.getenv("ZXBROKER_ACCOUNT", ""),
            broker_encoded_password=os.getenv("ZXBROKER_ENCODED_PASSWORD", ""),
            broker_sign_key=os.getenv("ZXBROKER_SIGN_KEY", ""),
            broker_intact_to_server=os.getenv("ZXBROKER_INTACT_TO_SERVER", ""),
            broker_token=broker_token or None,
            broker_timeout_seconds=max(
                1.0, _env_float("ZXBROKER_TIMEOUT_SECONDS", 20)
            ),
            broker_token_expire_minutes=30,
            broker_ocr_timeout_seconds=max(
                1.0, _env_float("ZXBROKER_OCR_TIMEOUT_SECONDS", 20)
            ),
            broker_ocr_request_interval_seconds=max(
                0.0,
                _env_float("ZXBROKER_OCR_REQUEST_INTERVAL_SECONDS", 1.05),
            ),
            broker_baidu_ocr_api_key=os.getenv("BAIDU_OCR_API_KEY", ""),
            broker_baidu_ocr_secret_key=os.getenv("BAIDU_OCR_SECRET_KEY", ""),
            broker_captcha_max_retries=min(
                3, max(1, _env_int("ZXBROKER_CAPTCHA_MAX_RETRIES", 3))
            ),
            broker_login_failure_cooldown_seconds=max(
                1,
                _env_int("ZXBROKER_LOGIN_FAILURE_COOLDOWN_SECONDS", 30),
            ),
            broker_max_order_count=max(
                1, _env_int("ZXBROKER_MAX_ORDER_COUNT", 100)
            ),
            broker_history_page_size=max(
                1, _env_int("ZXBROKER_HISTORY_PAGE_SIZE", 500)
            ),
            broker_history_max_records=max(
                1, _env_int("ZXBROKER_HISTORY_MAX_RECORDS", 5000)
            ),
            broker_history_request_interval_seconds=max(
                0.0,
                _env_float("ZXBROKER_HISTORY_REQUEST_INTERVAL_SECONDS", 0.2),
            ),
            minute_market_workers=min(
                16, max(1, _env_int("MINUTE_MARKET_WORKERS", 6))
            ),
            minute_market_request_interval_seconds=max(
                0.25,
                _env_float("MINUTE_MARKET_REQUEST_INTERVAL_SECONDS", 0.5),
            ),
            trading_min_price=TRADING_MIN_PRICE,
            trading_max_price=TRADING_MAX_PRICE,
            trading_order_fee=TRADING_ORDER_FEE,
            exit_position=EXIT_POSITION,
            trading_max_holding_days=TRADING_MAX_HOLDING_DAYS,
            trading_limit_down_lookback_days=TRADING_LIMIT_DOWN_LOOKBACK_DAYS,
            trading_max_recent_limit_downs=TRADING_MAX_RECENT_LIMIT_DOWNS,
            trading_allowed_prefixes=tuple(
                value.strip().upper()
                for value in os.getenv(
                    "STOCK_TRADING_ALLOWED_PREFIXES", "SH60,SZ00"
                ).split(",")
                if value.strip()
            ),
            trading_max_prediction_age_days=max(
                1, _env_int("STOCK_TRADING_MAX_PREDICTION_AGE_DAYS", 7)
            ),
            trading_execution_strategy=FIXED_TIME_URGENCY_STRATEGY,
            trading_order_wait_seconds=ORDER_WAIT_SECONDS,
            trading_order_poll_seconds=ORDER_POLL_SECONDS,
            trading_cancel_confirm_seconds=CANCEL_CONFIRM_SECONDS,
            trading_max_attempts=MAX_ATTEMPTS,
            trading_passive_fraction=PASSIVE_FRACTION,
            trading_normal_fraction=NORMAL_FRACTION,
            backtest_capital=max(
                1, _env_int("BACKTEST_CAPITAL", BACKTEST_CAPITAL)
            ),
            model_evaluation_observations=max(
                3,
                _env_int("MODEL_EVALUATION_OBSERVATIONS", 6),
            ),
            model_max_drawdown=min(
                1.0, max(0.0, _env_float("MODEL_MAX_DRAWDOWN", 0.10))
            ),
            model_min_total_gross_return=_env_float(
                "MODEL_MIN_TOTAL_GROSS_RETURN", 0
            ),
            model_min_positive_month_ratio=MODEL_MIN_POSITIVE_MONTH_RATIO,
            data_quality_min_ratio=min(
                1.0,
                max(
                    0.0,
                    _env_float("STOCK_TRADING_DATA_QUALITY_MIN_RATIO", 0.8),
                ),
            ),
        )


def load_project_environment() -> Path:
    source_project_dir = Path(__file__).resolve().parents[2]
    project_dir = (
        source_project_dir if os.name == "nt" else Path.home() / "stock-trading"
    )
    if project_dir not in _LOADED_ENV_ROOTS:
        _load_env_files(project_dir)
        _LOADED_ENV_ROOTS.add(project_dir)
    return project_dir


def _load_env_files(project_dir: Path) -> None:
    env_files = sorted(project_dir.glob("*.env"), key=lambda path: path.name)
    if not env_files:
        LOGGER.warning("未找到环境变量文件：%s/*.env", project_dir)
    for env_file in env_files:
        try:
            lines = env_file.read_text(encoding="utf-8-sig").splitlines()
        except OSError as error:
            LOGGER.warning("环境变量文件读取失败：%s；原因=%s", env_file, error)
            continue
        for line_number, raw_line in enumerate(lines, start=1):
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                LOGGER.warning(
                    "环境变量配置已忽略：文件=%s；行=%d；原因=缺少等号",
                    env_file,
                    line_number,
                )
                continue
            name, value = line.split("=", 1)
            name = name.strip()
            if not ENV_NAME_PATTERN.fullmatch(name):
                LOGGER.warning(
                    "环境变量配置已忽略：文件=%s；行=%d；原因=变量名无效",
                    env_file,
                    line_number,
                )
                continue
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            os.environ[name] = value
    missing_names = [name for name in REQUIRED_ENV_NAMES if not os.getenv(name)]
    if missing_names:
        LOGGER.warning(
            "环境变量缺失：%s；发布和非券商命令不受阻断，使用券商功能时将报错",
            ", ".join(missing_names),
        )


def _optional_env(name: str) -> str | None:
    value = os.getenv(name)
    return value if value else None


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return int(value)
    except ValueError:
        raise ValueError(f"环境变量 {name} 必须是整数") from None


def _env_float(name: str, default: float) -> float:
    value = os.getenv(name)
    if value is None or not value.strip():
        return default
    try:
        return float(value)
    except ValueError:
        raise ValueError(f"环境变量 {name} 必须是数字") from None
# AI_GENERATE_END-5--------- ---------------------------------------------------------
