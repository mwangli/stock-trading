# AI_GENERATE_START ----------------------------------------
from __future__ import annotations

import logging
import time
from collections.abc import Callable
from datetime import date

import pandas as pd

from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.storage import CollectionPlan, ParquetMarketStore
from stock_trading.logging_utils import log_context, set_log_context

LOGGER = logging.getLogger(__name__)


def research_codes(
    stocks: pd.DataFrame, start_date: date, end_date: date
) -> list[str]:
    """返回生命周期与研究窗口有交集的主板历史研究股票。"""
    codes: list[str] = []
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(("sh.60", "sz.00")):
            continue
        ipo_date = pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        out_date = pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        if ipo_date and ipo_date > end_date:
            continue
        if out_date and out_date < start_date:
            continue
        codes.append(code)
    return sorted(set(codes))


def live_codes(stocks: pd.DataFrame, snapshot_date: date) -> list[str]:
    """按生命周期返回候选池；仅同日基础快照使用快照状态。"""
    codes: list[str] = []
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(("sh.60", "sz.00")):
            continue
        ipo_date = pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        out_date = pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        metadata_date = (
            pd.Timestamp(row.snapshot_date).date()
            if pd.notna(row.snapshot_date)
            else None
        )
        if ipo_date and ipo_date > snapshot_date:
            continue
        if out_date and out_date < snapshot_date:
            continue
        if metadata_date == snapshot_date and (
            str(row.status) not in {"", "1"} or str(row.trade_status) != "1"
        ):
            continue
        codes.append(code)
    return sorted(set(codes))


def sync_stock_basics(
    store: ParquetMarketStore, proxy_url: str | None, snapshot_date: date
) -> dict[str, object]:
    """下载并保存股票基础信息。"""
    client = _open_client(proxy_url)
    try:
        stocks = client.fetch_stock_basics(snapshot_date)
    finally:
        client.__exit__(None, None, None)
    path = store.write_stock_basics(stocks)
    LOGGER.info("股票基础信息写入完成：数量=%d；文件=%s", len(stocks), path)
    return {"rows": len(stocks), "path": str(path)}


def sync_collection_data(
    store: ParquetMarketStore,
    proxy_url: str | None,
    codes: list[str],
    start_date: date,
    end_date: date,
    skip_existing: bool = False,
    plans: tuple[CollectionPlan, ...] | None = None,
) -> dict[str, object]:
    """在主线程使用单个长期 BaoStock 连接顺序采集股票数据。"""
    pending: list[CollectionPlan] = []
    downloaded = 0
    failed: dict[str, dict[str, str]] = {}
    if plans is not None:
        pending.extend(plans)
    else:
        for code in codes:
            needs_daily = not skip_existing or not store.daily_history_valid(
                code, start_date, end_date
            )
            needs_adjustment = not skip_existing or not store.adjustment_factors_valid(
                code, end_date
            )
            needs_minute5 = not skip_existing or not store.minute_summaries_valid(
                code, start_date, end_date
            )
            if any((needs_daily, needs_adjustment, needs_minute5)):
                pending.append(
                    CollectionPlan(
                        code=code,
                        needs_daily=needs_daily,
                        needs_adjustment=needs_adjustment,
                        needs_minute5=needs_minute5,
                    )
                )
    skipped = len(codes) - len(pending)

    set_log_context(progress=f"0/{len(pending)}", target="股票数据")
    LOGGER.info(
        "采集计划：目标=%d；待采集=%d；跳过=%d；连接=1",
        len(codes),
        len(pending),
        skipped,
    )
    if not pending:
        LOGGER.info("股票数据采集完成：新增=0；跳过=%d；失败=0", skipped)
        return {
            "completed": len(codes),
            "skipped": skipped,
            "failed": failed,
        }

    client = _open_client(proxy_url)
    try:
        for index, plan in enumerate(pending, start=1):
            with log_context(progress=f"{index}/{len(pending)}", target=plan.code):
                result, client = _sync_collection_code(
                    store,
                    client,
                    proxy_url,
                    plan.code,
                    start_date,
                    end_date,
                    plan.needs_daily,
                    plan.needs_adjustment,
                    plan.needs_minute5,
                )
                downloaded = _record_collection_result(result, downloaded, failed)
    finally:
        client.__exit__(None, None, None)
    set_log_context(progress=f"{len(pending)}/{len(pending)}", target="股票数据")
    LOGGER.info(
        "采集完成：新增=%d；跳过=%d；失败=%d",
        downloaded,
        skipped,
        len(failed),
    )
    result = {
        "completed": downloaded + skipped,
        "skipped": skipped,
        "failed": failed,
    }
    if failed:
        details = "；".join(
            f"{code}({','.join(sorted(errors))})"
            for code, errors in sorted(failed.items())
        )
        raise ValueError(f"股票数据采集失败：{details}")
    return result


def _record_collection_result(
    result: dict[str, object],
    downloaded: int,
    failed: dict[str, dict[str, str]],
) -> int:
    code = str(result["code"])
    errors = {
        str(dataset): str(error)
        for dataset, error in dict(result.get("errors") or {}).items()
    }
    if errors:
        failed[code] = errors
        LOGGER.warning(
            "股票数据采集失败：股票=%s；数据=%s",
            code,
            "；".join(f"{dataset}={error}" for dataset, error in errors.items()),
        )
    else:
        downloaded += 1
    return downloaded


def _open_client(proxy_url: str | None) -> BaoStockClient:
    for attempt in range(1, 4):
        client = BaoStockClient(proxy_url)
        try:
            return client.__enter__()
        except (BaoStockError, OSError, TimeoutError) as error:
            if "10001011" in str(error):
                raise BaoStockError(f"BaoStock IP 已进入黑名单：{error}") from error
            if attempt == 3:
                raise
            LOGGER.warning(
                "BaoStock 连接失败，稍后重试：尝试=%d/3；原因=%s",
                attempt,
                error,
            )
            time.sleep(attempt * 3)
    raise RuntimeError("BaoStock 连接重试意外结束")


def _sync_collection_code(
    store: ParquetMarketStore,
    client: BaoStockClient,
    proxy_url: str | None,
    code: str,
    start_date: date,
    end_date: date,
    needs_daily: bool,
    needs_adjustment: bool,
    needs_minute5: bool,
) -> tuple[dict[str, object], BaoStockClient]:
    errors: dict[str, str] = {}

    if needs_daily:
        def collect_daily(active_client: BaoStockClient) -> None:
            daily = active_client.fetch_daily_history(code, start_date, end_date)
            if not daily:
                raise ValueError("日线为空")
            store.write_daily_history(code, daily)

        error, client = _collect_component(
            client, proxy_url, code, "日线", collect_daily
        )
        if error:
            errors["日线"] = error

    if needs_adjustment:
        def collect_adjustment(active_client: BaoStockClient) -> None:
            factors = active_client.fetch_adjustment_factors(
                code, date(1990, 1, 1), end_date
            )
            store.write_adjustment_factors(code, factors)

        error, client = _collect_component(
            client, proxy_url, code, "复权因子", collect_adjustment
        )
        if error:
            errors["复权因子"] = error

    if needs_minute5:
        def collect_minute5(active_client: BaoStockClient) -> None:
            minutes = active_client.fetch_minutes(code, start_date, end_date)
            if not minutes:
                raise ValueError("5分钟线为空")
            store.write_minute_history(code, minutes, end_date)

        error, client = _collect_component(
            client, proxy_url, code, "5分钟线", collect_minute5
        )
        if error:
            errors["5分钟线"] = error

    return {"code": code, "errors": errors}, client


def _collect_component(
    client: BaoStockClient,
    proxy_url: str | None,
    code: str,
    dataset_name: str,
    operation: Callable[[BaoStockClient], None],
) -> tuple[str | None, BaoStockClient]:
    for attempt in range(1, 4):
        try:
            LOGGER.info(
                "正在采集%s：股票=%s；尝试=%d/3",
                dataset_name,
                code,
                attempt,
            )
            operation(client)
            return None, client
        except (BaoStockError, OSError, TimeoutError) as error:
            if "10001011" in str(error):
                raise BaoStockError(f"BaoStock IP 已进入黑名单：{error}") from error
            client.__exit__(None, None, None)
            if attempt == 3:
                client = _open_client(proxy_url)
                return str(error), client
            LOGGER.warning(
                "%s采集失败，重建会话后重试：股票=%s；尝试=%d/3；原因=%s",
                dataset_name,
                code,
                attempt,
                error,
            )
            time.sleep(attempt * 3)
            client = _open_client(proxy_url)
        except Exception as error:
            return str(error), client
    return "未知采集失败", client
# AI_GENERATE_END ----------------------------------------
