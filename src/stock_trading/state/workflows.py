# AI_GENERATE_START -
from __future__ import annotations

from contextlib import contextmanager
from collections.abc import Iterator
import logging
from datetime import date
from pathlib import Path

from stock_trading.config import AppConfig
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.state.store import StateStore
from stock_trading.utils.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)



@contextmanager
def _record_task(store: StateStore, task_type: str, business_date: date) -> Iterator[None]:
    """记录一次完整工作流；异常原样向上传递。"""
    business_key = store.start_task(task_type, business_date)
    try:
        yield
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)



def cleanup_workflow(config: AppConfig, reference_date: date) -> dict[str, object]:
    """按统一基准日期清理可淘汰状态，不删除长期业务事实。"""
    LOGGER.info("开始清理持久化数据：基准日期=%s", reference_date)
    store = StateStore(config.state_db_path)
    business_key = store.start_task("CLEAN", reference_date)
    before_bytes = _cleanup_storage_bytes(config)
    try:
        market = ParquetMarketStore(config.market_dir).cleanup_minutes(reference_date)
        state = store.cleanup(reference_date)
        after_bytes = _cleanup_storage_bytes(config)
        result = {
            "referenceDate": reference_date.isoformat(),
            "market": market,
            "state": state,
            "storage": {
                "beforeBytes": before_bytes,
                "afterBytes": after_bytes,
                "releasedBytes": max(0, before_bytes - after_bytes),
            },
        }
        store.finish_task(business_key, True)
        return result
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise



def _cleanup_storage_bytes(config: AppConfig) -> int:
    paths = (
        config.market_dir / "raw" / "minute5",
        config.market_dir / "derived" / "minute5_summary",
    )
    total = sum(
        path.stat().st_size
        for root in paths
        if root.exists()
        for path in root.rglob("*")
        if path.is_file()
    )
    for suffix in ("", "-wal", "-shm", "-journal"):
        path = Path(f"{config.state_db_path}{suffix}")
        if path.is_file():
            total += path.stat().st_size
    return total
# AI_GENERATE_END -
