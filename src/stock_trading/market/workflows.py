# AI_GENERATE_START -
from __future__ import annotations

from contextlib import contextmanager
from collections.abc import Iterator
import logging
from datetime import date

import pandas as pd

from stock_trading.config import AppConfig
from stock_trading.market.domain import (
    latest_completed_trade_date, live_codes, research_codes,
    shanghai_now, shanghai_today, trade_dates, trading_dates_between,
)
from stock_trading.market.quality import require_data_quality, research_coverage_ranges
import stock_trading.market.quality as quality
from stock_trading.market.sources import l1_market_provider, market_provider
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.transform import prepare_daily_derivatives
from stock_trading.state.store import StateStore
from stock_trading.utils.logging_utils import log_context

LOGGER = logging.getLogger(__name__)
COLLECTION_LEVELS = {1: ("daily",), 2: ("minute5",), 3: ("minute1",)}



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



def collect_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    requested_codes: list[str] | None = None,
    level: int = 1,
) -> dict[str, object]:
    """按数据层级顺序幂等采集；每一层都可从数据内容恢复。"""
    if level not in COLLECTION_LEVELS:
        raise ValueError("数据层级只能是 1、2 或 3")
    collection_types = _collection_types(level)

    state = StateStore(config.state_db_path)
    store = ParquetMarketStore(config.market_dir)
    today = shanghai_today()
    try:
        stocks, stock_result = ensure_stock_info(config, store)
    except Exception as error:
        stock_task = state.start_task("COLLECT_STOCK", today)
        state.finish_task(stock_task, False, error)
        return {
            "status": "PARTIAL", "types": list(collection_types), "level": level,
            "startDate": start_date.isoformat(), "endDate": end_date.isoformat(),
            "collection": {"ensure_stock_info": {"status": "FAILED", "error": str(error)}},
            "errors": {"ensure_stock_info": str(error)},
        }
    stock_task = state.start_task("COLLECT_STOCK", today)
    state.finish_task(stock_task, True)
    collection: dict[str, object] = {"ensure_stock_info": stock_result}
    errors: dict[str, str] = {}
    codes = _collection_codes(stocks, start_date, end_date, requested_codes)
    for collection_type in collection_types:
        task_date = end_date
        business_key = state.start_task(
            f"COLLECT_{collection_type.upper()}", task_date
        )
        try:
            result = _collect_type(
                config,
                store,
                collection_type,
                start_date,
                end_date,
                stocks,
                codes,
            )
        except Exception as error:
            state.finish_task(business_key, False, error)
            errors[collection_type] = str(error)
            collection[collection_type] = {
                "status": "FAILED",
                "error": str(error),
            }
            LOGGER.error(
                "采集阶段失败：类型=%s；原因=%s", collection_type, error
            )
        else:
            state.finish_task(business_key, True)
            collection[collection_type] = {"status": "SUCCESS"} | result
            LOGGER.info("采集阶段完成：类型=%s", collection_type)

    summary = {
        "status": "PARTIAL" if errors else "SUCCESS",
        "types": list(collection_types),
        "level": level,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "collection": collection,
        "errors": errors,
    }
    return summary



def _collection_types(level: int) -> tuple[str, ...]:
    return COLLECTION_LEVELS[level]



def _collect_type(
    config: AppConfig,
    store: ParquetMarketStore,
    collection_type: str,
    start_date: date,
    end_date: date,
    stocks: pd.DataFrame | None = None,
    codes: list[str] | None = None,
) -> dict[str, object]:
    if stocks is None or codes is None:
        raise ValueError("非基础采集缺少股票范围")
    if collection_type == "daily":
        return _collect_daily(config, store, stocks, codes, start_date, end_date)
    if collection_type == "minute5":
        return _collect_minute5(config, store, codes, start_date, end_date)
    if collection_type == "minute1":
        return _collect_minute(config, store, codes, start_date, end_date)
    raise ValueError(f"不支持的采集类型：{collection_type}")



def ensure_stock_info(
    config: AppConfig, store: ParquetMarketStore
) -> tuple[pd.DataFrame, dict[str, object]]:
    """检查并按需补齐股票基础信息，返回本次运行可复用的清单。"""
    provider = l1_market_provider(config)
    snapshot_date = latest_completed_trade_date(shanghai_now())
    if quality.stock_basics_valid(store, snapshot_date):
        result: dict[str, object] = {
            "skipped": True,
            "snapshotDate": snapshot_date.isoformat(),
        }
    else:
        result = provider.sync_stock_basics(store, snapshot_date)
    stock_quality = _stock_quality(store, snapshot_date)
    if not stock_quality["valid"]:
        raise ValueError("股票清单快照检查失败")
    stocks = store.read_stock_basics()
    return stocks, {
        "status": "SUCCESS",
        "skipped": bool(result.get("skipped")),
        "provider": provider.name,
        "scope": "full-market",
        "result": result,
        "dataQuality": stock_quality,
    }



def _collect_daily(
    config: AppConfig,
    store: ParquetMarketStore,
    stocks: pd.DataFrame,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    provider = l1_market_provider(config)
    dates = trading_dates_between(start_date, end_date)
    if not dates:
        raise ValueError("指定日期范围内没有交易日")
    coverage_ranges = research_coverage_ranges(
        stocks, codes, start_date, end_date
    )
    end_dates = {
        code: coverage_ranges[code][1]
        for code in codes
    }
    start_dates = {
        code: coverage_ranges[code][0]
        for code in codes
    }
    pending_codes = [
        code
        for code in codes
        if not quality.daily_history_valid(store,
            code, start_dates[code], end_dates[code]
        )
    ]
    if pending_codes:
        result = provider.sync_daily(store, stocks, pending_codes, dates)
    else:
        result = {"skipped": True, "reason": "daily 数据已完整"}
    usable_codes = sum(
        quality.daily_history_valid(store, code, start_dates[code], end_dates[code])
        for code in codes
    )
    code_quality = require_data_quality(
        "collect-daily-codes",
        len(codes),
        usable_codes,
        config.data_quality_min_ratio,
    )
    return {
        "skipped": not pending_codes,
        "provider": provider.name,
        "researchCodes": len(codes),
        "tradeDates": len(dates),
        "pendingCodes": len(pending_codes),
        "result": result,
        "dataQuality": code_quality,
    }



def _collect_minute5(
    config: AppConfig,
    store: ParquetMarketStore,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    dates = trading_dates_between(start_date, end_date)
    if not dates:
        raise ValueError("指定日期范围内没有交易日")
    provider = market_provider(config, "baostock")
    batches = _minute5_date_batches(dates)
    batch_results: list[dict[str, object]] = []
    fetched_rows = 0
    fetched_codes = 0
    failed_codes = 0
    usable_codes: set[str] = set()
    for index, batch in enumerate(batches, start=1):
        batch_start, batch_end = batch[0], batch[-1]
        with log_context(
            progress=f"{index}/{len(batches)}",
            target=f"{batch_start}:{batch_end}",
        ):
            ready = {
                code
                for code in codes
                if quality.minute_collection_ready(
                    store, code, batch_start, batch_end, expected_dates=set(batch)
                )
            }
            usable_codes.update(ready)
            pending = [code for code in codes if code not in ready]
            LOGGER.info(
                "5分钟线日期批次开始：日期=%s 至 %s；交易日=%d；待采股票=%d",
                batch_start,
                batch_end,
                len(batch),
                len(pending),
            )
            fetched = (
                provider.sync_minute5(
                    store, pending, batch_start, batch_end, expected_dates=set(batch)
                )
                if pending
                else {"skipped": True, "reason": "该日期批次已完整"}
            )
            fetched_rows += int(fetched.get("rows", 0))
            fetched_codes += int(fetched.get("codes", 0))
            failed_codes += int(fetched.get("failedCodes", 0))
            completed_codes = fetched.pop("completedCodes", [])
            usable_codes.update(str(code) for code in completed_codes)
            batch_results.append(
                {
                    "startDate": batch_start.isoformat(),
                    "endDate": batch_end.isoformat(),
                    "tradeDates": len(batch),
                    "pendingCodes": len(pending),
                    "fetch": fetched,
                }
            )
            LOGGER.info(
                "5分钟线日期批次完成：日期=%s 至 %s；采集股票=%d；失败股票=%d",
                batch_start,
                batch_end,
                int(fetched.get("codes", 0)),
                int(fetched.get("failedCodes", 0)),
            )
    usable = len(usable_codes)
    minute5_quality = require_data_quality(
        "collect-minute5",
        len(codes),
        usable,
        config.data_quality_min_ratio,
    )
    return {
        "skipped": all(
            bool(item["fetch"].get("skipped")) for item in batch_results
        ),
        "provider": provider.name,
        "researchCodes": len(codes),
        "result": {
            "progressUnit": "date-range",
            "batchUnit": "full-date-range",
            "batches": len(batches),
            "fetchedRows": fetched_rows,
            "fetchedCodes": fetched_codes,
            "failedCodes": failed_codes,
            "batchResults": batch_results,
        },
        "dataQuality": minute5_quality,
    }



def _collect_minute(
    config: AppConfig,
    store: ParquetMarketStore,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    provider = market_provider(config, "zxbroker")
    result = provider.sync_minute(store, codes, start_date, end_date)
    minute_quality = require_data_quality(
        "collect-minute",
        len(codes),
        len(quality.valid_minute1_raw_codes(store, codes, start_date, end_date)),
        config.data_quality_min_ratio,
    )
    return {
        "skipped": bool(result.get("targetCodeDates", 0))
        and int(result.get("targetCodeDates", 0)) == int(result.get("skippedCodeDates", 0)),
        "provider": provider.name,
        "researchCodes": len(codes),
        "result": result,
        "dataQuality": minute_quality,
    }



def _stock_quality(
    store: ParquetMarketStore, snapshot_date: date
) -> dict[str, object]:
    return {
        "stage": "collect-stock",
        "valid": bool(quality.stock_basics_valid(store, snapshot_date)),
        "snapshotDate": snapshot_date.isoformat(),
    }



def _collection_codes(
    stocks: pd.DataFrame,
    start_date: date,
    end_date: date,
    requested_codes: list[str] | None,
) -> list[str]:
    available_codes = (
        live_codes(stocks, end_date)
        if start_date == end_date
        else research_codes(stocks, start_date, end_date)
    )
    if requested_codes:
        unknown = sorted(set(requested_codes) - set(available_codes))
        if unknown:
            raise ValueError(f"股票不在当前采集范围：{','.join(unknown)}")
        return requested_codes
    if not available_codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的股票")
    return available_codes



def derive_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    """将 raw 原始事实转换为可供 prepare 独立消费的 derived 数据。"""
    raw_store = ParquetMarketStore(config.market_dir)
    derived_store = ParquetMarketStore(config.market_dir, source="derived")
    for year in range(start_date.year, end_date.year + 1):
        try:
            trade_dates(year)
        except (FileNotFoundError, OSError, ValueError) as error:
            raise ValueError(
                f"缺少 {year} 年本地交易日历；请先执行 stock data-collect"
            ) from error
    try:
        stocks = raw_store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到原始股票基础信息；请先执行 stock data-collect --level 1"
        ) from error
    derived_store.write_derived_stock_basics(stocks)
    codes = research_codes(stocks, start_date, end_date)
    coverage_ranges = research_coverage_ranges(stocks, codes, start_date, end_date)
    start_dates = {code: values[0] for code, values in coverage_ranges.items()}
    end_dates = {code: values[1] for code, values in coverage_ranges.items()}
    raw_daily_ready = [
        code
        for code in codes
        if quality.daily_history_valid(raw_store, code, start_dates[code], end_dates[code])
    ]
    result: dict[str, object] = {
        "indicators": prepare_daily_derivatives(
            raw_store,
            raw_daily_ready,
            start_date,
            end_date,
            start_dates=start_dates,
            end_dates=end_dates,
        ),
    }
    daily_ready = quality.valid_daily_codes(derived_store,
        raw_daily_ready,
        start_date,
        end_date,
        start_dates=start_dates,
        end_dates=end_dates,
    )
    result["minute5"] = raw_store.rebuild_minute_summaries(
        daily_ready, start_date, end_date, force=False
    )
    result["minute1"] = raw_store.rebuild_minute1_summaries(
        daily_ready, start_date, end_date
    )
    return {
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(codes),
        "derived": result,
    }


def _minute5_date_batches(dates: list[date]) -> list[list[date]]:
    """BaoStock 单连接按完整日期范围查询，避免按年度重复请求和写盘。"""
    return [dates] if dates else []
# AI_GENERATE_END -
