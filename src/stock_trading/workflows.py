# AI_GENERATE_START---------------------------------------- -------------------------------------
from __future__ import annotations

import json
import logging
from datetime import date, time
from contextlib import contextmanager
from collections.abc import Iterator
from pathlib import Path

import pandas as pd

from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.config import AppConfig
from stock_trading.utils.instruments import normalize_instrument
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.derived import prepare_daily_derivatives
from stock_trading.market.trade_calendar import trade_dates
from stock_trading.market.quality import (
    require_data_quality,
    research_coverage_ranges,
)
from stock_trading.market.providers import (
    l1_market_provider,
    market_provider,
)
from stock_trading.market.sync import (
    ensure_trade_calendar,
    resolve_completed_trade_date,
    live_codes,
    research_codes,
    sync_daily,
    sync_minute,
    sync_minute5,
    sync_stock_basics,
    trading_dates_between,
)
from stock_trading.utils.notifications import push_execution_result
from stock_trading.utils.logging_utils import log_context
from stock_trading.research.artifacts import (
    model_artifacts_valid,
    model_directory,
    prediction_artifact_path,
    load_valid_prediction_artifact,
)
from stock_trading.research.model_adapter import create_research_model
from stock_trading.research.model_config import ModelSpec
from stock_trading.state.store import StateStore
from stock_trading.trading.post_trade import BrokerOrderSyncService
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.post_trade import (
    ModelReviewService,
    RealizedProfitService,
)
from stock_trading.trading.post_trade import refresh_permissions
from stock_trading.utils.time_utils import shanghai_now, shanghai_today
from stock_trading.market.trade_calendar import shift_trade_date

LOGGER = logging.getLogger(__name__)
COLLECTION_TYPES = ("stock", "daily", "minute5", "minute1")


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
    required_fields: tuple[str, ...] | None = None,
    requested_types: tuple[str, ...] | None = None,
) -> dict[str, object]:
    """按固定顺序采集全部数据类型，单项失败不阻断后续阶段。"""
    collection_types = _collection_types(required_fields, requested_types)
    if requested_codes and "stock" in collection_types:
        LOGGER.warning("stock 始终写入全市场快照，--codes 对 stock 不生效")

    state = StateStore(config.state_db_path, calendar_dir=config.market_dir / "raw" / "calendar")
    store = ParquetMarketStore(config.market_dir)
    collection: dict[str, object] = {}
    errors: dict[str, str] = {}
    today = shanghai_today()
    stocks: pd.DataFrame | None = None
    codes: list[str] | None = None
    for collection_type in collection_types:
        task_date = today if collection_type == "stock" else end_date
        business_key = state.start_task(
            f"COLLECT_{collection_type.upper()}", task_date
        )
        try:
            if collection_type != "stock" and stocks is None:
                stocks = _local_stock_basics(store)
                codes = _collection_codes(
                    stocks, start_date, end_date, requested_codes
                )
            result = _collect_type(
                config,
                store,
                collection_type,
                start_date,
                end_date,
                requested_codes,
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
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "collection": collection,
        "errors": errors,
    }
    return summary


def _collection_types(
    required_fields: tuple[str, ...] | None,
    requested_types: tuple[str, ...] | None = None,
) -> tuple[str, ...]:
    if requested_types is not None:
        unknown = set(requested_types) - set(COLLECTION_TYPES)
        if unknown:
            raise ValueError(f"不支持的采集类型：{','.join(sorted(unknown))}")
        return tuple(name for name in COLLECTION_TYPES if name in requested_types)
    if required_fields is None:
        return COLLECTION_TYPES
    selected = {"stock"}
    field_types = {
        "daily": "daily",
        "factor": "daily",
        "basic": "daily",
        "minute_summary": "minute5",
        "minute5": "minute5",
        "minute1": "minute1",
    }
    selected.update(field_types[field] for field in required_fields if field in field_types)
    return tuple(name for name in COLLECTION_TYPES if name in selected)


def _collect_type(
    config: AppConfig,
    store: ParquetMarketStore,
    collection_type: str,
    start_date: date,
    end_date: date,
    requested_codes: list[str] | None,
    stocks: pd.DataFrame | None = None,
    codes: list[str] | None = None,
) -> dict[str, object]:
    if collection_type == "stock":
        return _collect_stock(config, store)
    if stocks is None or codes is None:
        raise ValueError("非基础采集缺少股票范围")
    if collection_type == "daily":
        return _collect_daily(config, store, stocks, codes, start_date, end_date)
    if collection_type == "minute5":
        return _collect_minute5(config, store, codes, start_date, end_date)
    if collection_type == "minute1":
        return _collect_minute(config, store, codes, start_date, end_date)
    raise ValueError(f"不支持的采集类型：{collection_type}")


def _collect_stock(
    config: AppConfig, store: ParquetMarketStore
) -> dict[str, object]:
    provider = l1_market_provider(config)
    snapshot_date = resolve_completed_trade_date(store, provider, shanghai_now())
    if store.stock_basics_valid(snapshot_date):
        result: dict[str, object] = {
            "skipped": True,
            "snapshotDate": snapshot_date.isoformat(),
        }
    else:
        result = sync_stock_basics(store, provider, snapshot_date)
    quality = _stock_quality(store, snapshot_date)
    if not quality["valid"]:
        raise ValueError("股票清单快照检查失败")
    return {
        "skipped": bool(result.get("skipped")),
        "provider": provider.name,
        "scope": "full-market",
        "result": result,
        "dataQuality": quality,
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
    dates = trading_dates_between(store, provider, start_date, end_date)
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
        if not store.daily_history_valid(
            code, start_dates[code], end_dates[code]
        )
    ]
    if pending_codes:
        result = sync_daily(store, provider, stocks, pending_codes, dates)
    else:
        result = {"skipped": True, "reason": "daily 数据已完整"}
    usable_codes = sum(
        store.daily_history_valid(code, start_dates[code], end_dates[code])
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
    dates = trading_dates_between(
        store, l1_market_provider(config), start_date, end_date
    )
    if not dates:
        raise ValueError("指定日期范围内没有交易日")
    provider = market_provider(config, "baostock")
    batches = _minute5_date_batches(dates)
    batch_results: list[dict[str, object]] = []
    fetched_rows = 0
    fetched_codes = 0
    failed_codes = 0
    for index, batch in enumerate(batches, start=1):
        batch_start, batch_end = batch[0], batch[-1]
        with log_context(
            progress=f"{index}/{len(batches)}",
            target=f"{batch_start}:{batch_end}",
        ):
            pending = [
                code
                for code in codes
                if not store.minute_history_valid(code, batch_start, batch_end)
            ]
            LOGGER.info(
                "5分钟线日期批次开始：日期=%s 至 %s；交易日=%d；待采股票=%d",
                batch_start,
                batch_end,
                len(batch),
                len(pending),
            )
            fetched = (
                sync_minute5(
                    store, provider, pending, batch_start, batch_end
                )
                if pending
                else {"skipped": True, "reason": "该日期批次已完整"}
            )
            fetched_rows += int(fetched.get("rows", 0))
            fetched_codes += int(fetched.get("codes", 0))
            failed_codes += int(fetched.get("failedCodes", 0))
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
    usable = sum(
        store.minute_history_valid(code, start_date, end_date)
        for code in codes
    )
    quality = require_data_quality(
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
            "batchUnit": "calendar-year",
            "batches": len(batches),
            "fetchedRows": fetched_rows,
            "fetchedCodes": fetched_codes,
            "failedCodes": failed_codes,
            "batchResults": batch_results,
        },
        "dataQuality": quality,
    }


def _collect_minute(
    config: AppConfig,
    store: ParquetMarketStore,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    provider = market_provider(config, "zxbroker")
    result = sync_minute(store, provider, codes, start_date, end_date)
    quality = require_data_quality(
        "collect-minute",
        len(codes),
        len(store.valid_minute1_raw_codes(codes, start_date, end_date)),
        config.data_quality_min_ratio,
    )
    return {
        "provider": provider.name,
        "researchCodes": len(codes),
        "result": result,
        "dataQuality": quality,
    }


def _local_stock_basics(store: ParquetMarketStore) -> pd.DataFrame:
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到本地股票基础信息；请先执行 stock-collect"
        ) from error
    if stocks.empty or "snapshot_date" not in stocks:
        raise ValueError(
            "本地股票基础信息无效；请先执行 stock-collect"
        )
    snapshot_dates = pd.to_datetime(
        stocks["snapshot_date"], errors="coerce"
    ).dropna()
    if snapshot_dates.empty:
        raise ValueError(
            "本地股票基础信息缺少快照日期；请先执行 stock-collect"
        )
    snapshot_date = snapshot_dates.max().date()
    today = shanghai_today()
    if snapshot_date != today:
        LOGGER.warning(
            "本地股票基础信息不是今日快照，仍继续执行：快照=%s；今日=%s",
            snapshot_date,
            today,
        )
    return stocks


def _stock_quality(
    store: ParquetMarketStore, snapshot_date: date
) -> dict[str, object]:
    return {
        "stage": "collect-stock",
        "valid": bool(store.stock_basics_valid(snapshot_date)),
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


def prepare_workflow(
    config: AppConfig,
    spec: ModelSpec,
    start_date: date,
    end_date: date,
    force: bool = False,
) -> dict[str, object]:
    """读取原始 Parquet，补齐可信年度日历后幂等准备指定范围的 Qlib。"""
    store = ParquetMarketStore(config.market_dir)
    ensure_trade_calendar(
        store, l1_market_provider(config), range(start_date.year, end_date.year + 1)
    )
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到股票基础信息；请先执行 stock-collect"
        ) from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
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
    LOGGER.info(
        "开始准备研究数据：股票数=%d；日期=%s 至 %s",
        len(codes),
        start_date,
        end_date,
    )
    raw_daily_ready = [
        code
        for code in codes
        if store.daily_history_valid(
            code, start_dates[code], end_dates[code]
        )
    ]
    derived: dict[str, object] = {}
    if {"basic", "factor"} & set(spec.required_fields):
        derived["daily"] = prepare_daily_derivatives(
            store,
            raw_daily_ready,
            start_date,
            end_date,
            start_dates=start_dates,
            end_dates=end_dates,
        )
    daily_ready = store.valid_daily_codes(
        raw_daily_ready,
        start_date,
        end_date,
        start_dates=start_dates,
        end_dates=end_dates,
    )
    if "basic" in spec.required_fields:
        basic_ready = set(
            store.valid_basic_codes(
                daily_ready,
                start_date,
                end_date,
                63,
                start_dates=start_dates,
                end_dates=end_dates,
            )
        )
        daily_ready = [code for code in daily_ready if code in basic_ready]
    if "factor" in spec.required_fields:
        factor_ready = {
            code
            for code in daily_ready
            if store.factors_valid(
                code, end_dates[code], start_dates[code]
            )
        }
        daily_ready = [code for code in daily_ready if code in factor_ready]
    if spec.feature_frequency == "minute5":
        derived["minute5"] = store.rebuild_minute_summaries(
            daily_ready, start_date, end_date
        )
    elif spec.feature_frequency == "minute1":
        derived["minute1"] = store.rebuild_minute1_summaries(
            daily_ready, start_date, end_date
        )
    ready_codes = store.valid_model_codes(
        daily_ready,
        spec.feature_frequency,
        start_date,
        end_date,
        63,
    )
    quality = require_data_quality(
        "prepare",
        len(codes),
        len(ready_codes),
        config.data_quality_min_ratio,
    )
    prepared = create_research_model(config, spec).prepare(
        ready_codes, start_date, end_date, force=force
    )
    LOGGER.info(
        "Qlib 准备完成：模式=%s；股票数=%d；日期=%s 至 %s",
        prepared["mode"],
        prepared["codes"],
        prepared["startDate"],
        prepared["endDate"],
    )
    return {
        "modelId": spec.model_id,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(ready_codes),
        "dataQuality": quality,
        "derived": derived,
        "qlib": prepared,
    }


def _minute5_date_batches(dates: list[date]) -> list[list[date]]:
    """BaoStock 单连接按完整日期范围查询，避免按年度重复请求和写盘。"""
    return [dates] if dates else []


def cleanup_workflow(config: AppConfig, reference_date: date) -> dict[str, object]:
    """按统一基准日期清理可淘汰状态，不删除长期业务事实。"""
    LOGGER.info("开始清理持久化数据：基准日期=%s", reference_date)
    store = StateStore(config.state_db_path, calendar_dir=config.market_dir / "raw" / "calendar")
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


def sync_order_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    broker: ZXBrokerClient | None = None,
) -> dict[str, object]:
    """同步券商已成交事实、费用和权限，并完成可闭环的 FIFO 收益。"""
    LOGGER.info("开始同步券商历史订单：日期=%s 至 %s", start_date, end_date)
    store = StateStore(config.state_db_path, calendar_dir=config.market_dir / "raw" / "calendar")
    active_broker = broker or _build_broker(config)
    with _record_task(store, "ORDER", end_date):
        account_snapshot = store.save_account_snapshot(
            active_broker.get_account()
        )
        LOGGER.info(
            "订单同步前账户快照已更新：采集时间=%s；可用资金=%s；"
            "总资产=%s；持仓市值=%s；冻结金额=%s",
            account_snapshot["captured_at"],
            account_snapshot["available_cash"],
            account_snapshot["total_assets"],
            account_snapshot["total_position"],
            account_snapshot["frozen_amount"],
        )
        result = BrokerOrderSyncService(config, store, active_broker).sync(
            start_date, end_date
        )
        result["accountSnapshot"] = account_snapshot
        result["permissions"] = refresh_permissions(
            store, store.broker_orders(), end_date
        )
    LOGGER.info(
        "券商历史订单同步完成：日期=%s 至 %s；有效订单=%s；新增闭环=%s",
        start_date,
        end_date,
        result["validOrders"],
        result["settlement"]["realizedTrades"],
    )
    return result


def rank_workflow(
    config: AppConfig, spec: ModelSpec, prediction_date: date | None
) -> dict[str, object]:
    """收盘且数据就绪后生成 D 信号，明确用于下一交易日买入。"""
    now = shanghai_now()
    store = ParquetMarketStore(config.market_dir)
    provider = l1_market_provider(config)
    calendar_dir = config.market_dir / "raw" / "calendar"
    if prediction_date is None:
        prediction_date = resolve_completed_trade_date(store, provider, now)
    if prediction_date > now.date() or (
        prediction_date == now.date() and now.time() < time(15, 0)
    ):
        raise ValueError("排名只能使用已经收盘的交易日数据")
    ensure_trade_calendar(store, provider, (prediction_date.year,))
    if prediction_date == trade_dates(prediction_date.year, calendar_dir)[-1]:
        ensure_trade_calendar(store, provider, (prediction_date.year + 1,))
    execution_date = shift_trade_date(prediction_date, 1, calendar_dir)
    LOGGER.info("开始生成完整横截面预测：预测日期=%s", prediction_date)
    state_store = StateStore(config.state_db_path, calendar_dir=config.market_dir / "raw" / "calendar")
    _assert_task_success(state_store, "COLLECT_DAILY", prediction_date)
    if spec.feature_frequency == "minute5":
        _assert_task_success(state_store, "COLLECT_MINUTE5", prediction_date)
    model = create_research_model(config, spec)
    stocks = store.read_stock_basics()
    metadata_codes = live_codes(stocks, prediction_date)
    if spec.feature_frequency == "minute5":
        current_codes = store.minute5_codes_on(metadata_codes, prediction_date)
    else:
        current_codes = metadata_codes
    expected_instruments = {
        normalize_instrument(code) for code in current_codes
    }
    available_instruments = model.available_instruments(prediction_date)
    if available_instruments is None:
        raise ValueError(
            f"Qlib 未覆盖目标日期：{prediction_date.isoformat()}；"
            "请先执行 stock-prepare"
        )
    ready_instruments = expected_instruments & available_instruments
    quality = require_data_quality(
        "rank",
        len(expected_instruments),
        len(ready_instruments),
        config.data_quality_min_ratio,
    )
    model_dir = model_directory(config.model_dir, spec)
    if not model_artifacts_valid(model_dir, spec):
        raise ValueError(
            f"模型制品不存在或不完整：{spec.model_id}；请先执行 stock-train"
        )
    prediction_path = prediction_artifact_path(model_dir, prediction_date)
    existing = _existing_prediction_summary(
        model_dir,
        prediction_path,
        prediction_date,
        ready_instruments,
        calendar_dir,
    )
    if existing is None:
        prediction = model.rank(
            prediction_date, eligible_instruments=ready_instruments
        )
        state = state_store.import_prediction_artifact(
            model_dir, Path(str(prediction["path"]))
        )
    else:
        prediction = existing
        if state_store.prediction_registered(
            model_dir / "model.pkl", prediction_date.isoformat(), prediction_path
        ):
            state = {
                "status": "SUCCESS",
                "predictionDate": prediction_date.isoformat(),
                "predictions": prediction["rankingRows"],
                "reused": True,
            }
        else:
            state = state_store.import_prediction_artifact(
                model_dir, prediction_path
            )
        LOGGER.info(
            "现有横截面排名检查通过，跳过重复计算：日期=%s；股票数=%d",
            prediction_date,
            prediction["rankingRows"],
        )
    LOGGER.info(
        "横截面预测完成：全市场评分数=%s；排名数=%s；文件=%s",
        prediction["universeRows"],
        prediction["rankingRows"],
        prediction["path"],
    )
    return {
        "modelId": spec.model_id,
        "featureDate": prediction_date.isoformat(),
        "executionDate": execution_date.isoformat(),
        "prediction": prediction,
        "state": state,
        "dataQuality": quality,
    }


def _existing_prediction_summary(
    model_dir: Path,
    path: Path,
    prediction_date: date,
    expected_instruments: set[str],
    calendar_dir: Path,
) -> dict[str, object] | None:
    frame = load_valid_prediction_artifact(model_dir, prediction_date, calendar_dir)
    if frame is None:
        return None
    actual_instruments = {
        normalize_instrument(value) for value in frame["instrument"]
    }
    if actual_instruments != expected_instruments:
        return None
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    return {
        "modelId": metrics["modelId"],
        "model": metrics["model"],
        "featureSet": metrics["featureSet"],
        "predictionDate": prediction_date.isoformat(),
        "universeRows": len(frame),
        "rankingRows": len(frame),
        "path": str(path),
        "topCandidates": [
            {
                "instrument": str(row.instrument),
                "rank": int(row.rank),
                "score": float(row.score),
            }
            for row in frame.head(5).itertuples(index=False)
        ],
    }


def trading_workflow(
    config: AppConfig, spec: ModelSpec, business_date: date, side: str
) -> dict[str, object]:
    """选择实时交易目标并逐个执行，返回可供 CLI 输出的完整摘要。"""
    if side not in {"BUY", "SELL"}:
        raise ValueError("交易方向必须是 BUY 或 SELL")
    now = shanghai_now()
    window = spec.strategy.buy_window if side == "BUY" else spec.strategy.sell_window
    start_time, end_time = (time.fromisoformat(value) for value in window)
    if now.date() != business_date or not start_time <= now.time() < end_time:
        action = "买入" if side == "BUY" else "卖出"
        raise ValueError(
            f"{action}只能在 {start_time.strftime('%H:%M')}-"
            f"{end_time.strftime('%H:%M')} 执行"
        )
    calendar_dir = config.market_dir / "raw" / "calendar"
    calendar_store = ParquetMarketStore(config.market_dir)
    provider = l1_market_provider(config)
    ensure_trade_calendar(calendar_store, provider, (business_date.year,))
    dates = trade_dates(business_date.year, calendar_dir)
    if business_date not in dates:
        raise ValueError(f"不是交易日: {business_date}")
    if side == "BUY" and business_date == dates[0]:
        ensure_trade_calendar(calendar_store, provider, (business_date.year - 1,))
    mode = f"{spec.data_level}_{side}"
    mode_name = "买入" if side == "BUY" else "卖出"
    LOGGER.info(
        "开始执行实时交易窗口：日期=%s；时段=%s；策略=%s；窗口=%d秒",
        business_date,
        mode_name,
        config.trading_execution_strategy,
        ((end_time.hour * 60 + end_time.minute) - (start_time.hour * 60 + start_time.minute)) * 60,
    )
    store = StateStore(config.state_db_path, calendar_dir=config.market_dir / "raw" / "calendar")
    broker = _build_broker(config)
    account_snapshot = store.save_account_snapshot(broker.get_account())
    LOGGER.info(
        "交易前账户快照已更新：采集时间=%s；可用资金=%s；总资产=%s；持仓市值=%s；冻结金额=%s",
        account_snapshot["captured_at"],
        account_snapshot["available_cash"],
        account_snapshot["total_assets"],
        account_snapshot["total_position"],
        account_snapshot["frozen_amount"],
    )
    execution = OrderExecutionOrchestrator(config, store, broker)
    model = create_research_model(config, spec)
    selection = LiveTradingOrchestrator(
        config, store, broker, spec.model_id, model.qlib_dir
    ).select_targets(business_date, side)
    executed: list[dict[str, object]] = []
    for target in selection["targets"]:
        result = execution.execute_target(business_date, target)
        executed.append(result)
        push_execution_result(config, business_date, result)
    LOGGER.info(
        "实时交易窗口完成：目标数=%d；执行结果数=%d",
        len(selection["targets"]),
        len(executed),
    )
    return {
        "modelId": spec.model_id,
        "businessDate": business_date.isoformat(),
        "mode": mode,
        "strategy": config.trading_execution_strategy,
        "accountSnapshot": account_snapshot,
        "selection": selection,
        "executed": executed,
    }


def review_workflow(
    config: AppConfig, spec: ModelSpec, business_date: date
) -> dict[str, object]:
    """汇总已实现收益并判断模型是否需要重训。"""
    store = StateStore(config.state_db_path, calendar_dir=config.market_dir / "raw" / "calendar")
    _assert_task_success(store, "ORDER", business_date)
    with _record_task(store, "REVIEW", business_date):
        profit = RealizedProfitService(store).summarize(
            spec.model_id, business_date
        )
        review = ModelReviewService(config, store).review(
            spec.model_id, business_date
        )
    return {
        "modelId": spec.model_id,
        "businessDate": business_date.isoformat(),
        "profit": profit,
        "monthlyProfit": review["monthlyProfit"],
        "decision": review["decision"],
    }


def _assert_task_success(
    store: StateStore, task_type: str, business_date: date
) -> None:
    task = store.task_status(task_type, business_date)
    if task is not None and task["status"] == "SUCCESS":
        return
    status = "MISSING" if task is None else str(task["status"])
    reason = "" if task is None else str(task.get("error_message") or "")
    detail = f"；原因={reason}" if reason else ""
    raise ValueError(
        f"前置任务未成功：{task_type}:{business_date.isoformat()}；"
        f"状态={status}{detail}"
    )


def _build_broker(config: AppConfig) -> ZXBrokerClient:
    return ZXBrokerClient(config, captcha_solver=BaiduCaptchaSolver(config).solve)


# AI_GENERATE_END----------------------------------------- -------------------------------------
