# AI_GENERATE_START ----------------------------
from __future__ import annotations

import logging
import time
from collections.abc import Callable
from datetime import date, timedelta
from multiprocessing import get_context
from queue import Empty
from typing import Any

import pandas as pd

from stock_trading.logging_utils import (
    bind_progress_context,
    configure_logging,
    restore_progress_context,
)
from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.progress import CollectionProgress
from stock_trading.market.storage import ParquetMarketStore

LOGGER = logging.getLogger(__name__)


def latest_trade_date(proxy_url: str | None) -> date:
    """查询最近一个存在全市场日线的交易日。"""
    with BaoStockClient(proxy_url) as client:
        for offset in range(15):
            candidate = date.today() - timedelta(days=offset)
            bars = client.fetch_daily_market(candidate)
            if bars:
                LOGGER.info("最近交易日确认：%s；股票数=%d", candidate, len(bars))
                return candidate
    raise ValueError("最近 15 个自然日内没有找到可用交易日")


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
    with BaoStockClient(proxy_url) as client:
        stocks = client.fetch_stock_basics(snapshot_date)
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
    worker_count: int = 1,
) -> dict[str, object]:
    """使用股票任务队列和多个长期 BaoStock 连接采集数据。"""
    pending: list[tuple[str, bool, bool, bool]] = []
    skipped = 0
    downloaded = 0
    failed: dict[str, dict[str, str]] = {}
    progress_total = len(codes) * 2 if skip_existing else len(codes)
    with CollectionProgress(
        total_count=progress_total,
    ) as progress:
        for checked, code in enumerate(codes, start=1):
            needs_daily = not skip_existing or not store.has_daily_history(code)
            needs_adjustment = not skip_existing or not store.has_adjustment_factors(code)
            needs_minute5 = not skip_existing or not store.has_minute5_data(code)
            if not any((needs_daily, needs_adjustment, needs_minute5)):
                skipped += 1
            else:
                pending.append((code, needs_daily, needs_adjustment, needs_minute5))
            if skip_existing:
                progress.update_check(checked + skipped)
                LOGGER.info(
                    "股票数据检查完成：股票=%s；进度=%d/%d；待采集=%d；已完整=%d",
                    code,
                    checked,
                    len(codes),
                    len(pending),
                    skipped,
                )

        collection_base = len(codes) + skipped if skip_existing else 0
        progress.begin_estimate(collection_base)
        active_workers = min(max(1, worker_count), len(pending)) if pending else 0
        LOGGER.info(
            "股票数据采集计划：目标=%d；待采集=%d；跳过=%d；连接=%d",
            len(codes),
            len(pending),
            skipped,
            active_workers,
        )
        if not pending:
            LOGGER.info(
                "股票数据采集完成：新增=0；跳过=%d；失败=0",
                skipped,
            )
            return {
                "completed": len(codes),
                "skipped": skipped,
                "failed": failed,
            }

        context = get_context("spawn")
        task_queue = context.Queue()
        result_queue = context.Queue()
        workers = [
            context.Process(
                target=_collection_worker,
                args=(
                    task_queue,
                    result_queue,
                    store.root,
                    proxy_url,
                    start_date,
                    end_date,
                    progress.worker_context,
                ),
                name=f"stock-collect-{index}",
            )
            for index in range(1, active_workers + 1)
        ]
        completed = 0
        collection_complete = False
        started_workers = []
        try:
            for worker in workers:
                worker.start()
                started_workers.append(worker)
            for plan in pending:
                task_queue.put(plan)
            for _ in started_workers:
                task_queue.put(None)

            while completed < len(pending):
                try:
                    result_type, payload = result_queue.get(timeout=1)
                except Empty:
                    if not any(worker.is_alive() for worker in started_workers):
                        raise RuntimeError("股票采集 worker 已全部退出") from None
                    continue
                if result_type == "fatal":
                    raise RuntimeError(f"股票采集 worker 异常退出：{payload}")
                downloaded, completed = _record_collection_result(
                    payload,
                    downloaded,
                    completed,
                    failed,
                    progress,
                    collection_base,
                )
            collection_complete = True
        finally:
            if not collection_complete:
                for worker in started_workers:
                    if worker.is_alive():
                        worker.terminate()
            for worker in started_workers:
                worker.join()
            failed_workers = [
                worker.name
                for worker in started_workers
                if collection_complete and worker.exitcode != 0
            ]
            task_queue.close()
            result_queue.close()
            if failed_workers:
                raise RuntimeError(
                    f"股票采集 worker 异常退出：{','.join(failed_workers)}"
                )
        LOGGER.info(
            "股票数据采集完成：新增=%d；跳过=%d；失败=%d",
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


def _collection_worker(
    task_queue: Any,
    result_queue: Any,
    store_root: Any,
    proxy_url: str | None,
    start_date: date,
    end_date: date,
    progress_context: tuple[Any, Any, int, Any],
) -> None:
    """从共享队列领取股票，并在进程内复用一个 BaoStock 会话。"""
    configure_logging()
    previous_context = bind_progress_context(*progress_context)
    client: BaoStockClient | None = None
    try:
        store = ParquetMarketStore(store_root)
        client = BaoStockClient(proxy_url)
        client.__enter__()
        while True:
            plan = task_queue.get()
            if plan is None:
                break
            result, client = _sync_collection_code(
                store,
                client,
                proxy_url,
                plan[0],
                start_date,
                end_date,
                plan[1],
                plan[2],
                plan[3],
            )
            result_queue.put(("result", result))
    except BaseException as error:
        result_queue.put(("fatal", str(error)))
    finally:
        if client is not None:
            client.__exit__(None, None, None)
        restore_progress_context(previous_context)


def _record_collection_result(
    result: dict[str, object],
    downloaded: int,
    completed: int,
    failed: dict[str, dict[str, str]],
    progress: CollectionProgress,
    collection_base: int,
) -> tuple[int, int]:
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
    completed += 1
    progress.update(collection_base + completed)
    return downloaded, completed


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
                client = BaoStockClient(proxy_url)
                client.__enter__()
                return str(error), client
            LOGGER.warning(
                "%s采集失败，重建会话后重试：股票=%s；尝试=%d/3；原因=%s",
                dataset_name,
                code,
                attempt,
                error,
            )
            time.sleep(attempt * 3)
            client = BaoStockClient(proxy_url)
            client.__enter__()
        except Exception as error:
            return str(error), client
    return "未知采集失败", client
# AI_GENERATE_END ----------------------------
