# AI_GENERATE_START --------------------
from __future__ import annotations

import atexit
import logging
import os
import signal
import time
from concurrent.futures import FIRST_COMPLETED, ProcessPoolExecutor, wait
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path

import pandas as pd

from stock_trading.logging_utils import bind_progress_context, configure_logging
from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.models import AdjustmentFactor
from stock_trading.market.progress import ResearchProgress
from stock_trading.market.storage import ParquetMarketStore

LOGGER = logging.getLogger(__name__)
MAX_BAOSTOCK_WORKERS = 4
PROCESS_POOL_POLL_SECONDS = 0.2
WORKER_SHUTDOWN_TIMEOUT_SECONDS = 5

_WORKER_STORE: ParquetMarketStore | None = None
_WORKER_PROXY_URL: str | None = None
_WORKER_CLIENT: BaoStockClient | None = None


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


def sync_research_data(
    store: ParquetMarketStore,
    proxy_url: str | None,
    codes: list[str],
    start_date: date,
    end_date: date,
    workers: int = 1,
) -> dict[str, object]:
    """使用独立进程和进程内复用会话幂等同步研究行情。"""
    pending: list[str] = []
    skipped = 0
    for code in codes:
        if store.research_data_covers(code, start_date, end_date):
            skipped += 1
        else:
            pending.append(code)

    worker_count = min(MAX_BAOSTOCK_WORKERS, max(1, workers), max(1, len(pending)))
    downloaded = 0
    last_rows = 0
    failed: dict[str, str] = {}
    with ResearchProgress(
        total_count=len(codes),
        skipped=skipped,
    ) as progress:
        LOGGER.info(
            "研究数据采集计划：目标=%d；待下载=%d；跳过=%d；进程=%d",
            len(codes),
            len(pending),
            skipped,
            worker_count,
        )
        if not pending:
            LOGGER.info(
                "研究数据采集完成：下载=0；跳过=%d；失败=0",
                skipped,
            )
            return {
                "downloaded": downloaded,
                "skipped": skipped,
                "failed": failed,
                "workers": worker_count,
                "lastRows": last_rows,
            }

        completed = 0
        if worker_count == 1:
            _initialize_research_worker(
                store.root,
                proxy_url,
                *progress.worker_context,
            )
            try:
                for code in pending:
                    result = _sync_research_code(code, start_date, end_date)
                    downloaded, completed, last_rows = _record_research_result(
                        result,
                        downloaded,
                        completed,
                        last_rows,
                        failed,
                        progress,
                    )
            finally:
                _close_worker_client()
        else:
            executor = ProcessPoolExecutor(
                max_workers=worker_count,
                initializer=_initialize_research_worker,
                initargs=(store.root, proxy_url, *progress.worker_context),
            )
            try:
                futures = {
                    executor.submit(_sync_research_code, code, start_date, end_date): code
                    for code in pending
                }
                pending_futures = set(futures)
                while pending_futures:
                    done, pending_futures = wait(
                        pending_futures,
                        timeout=PROCESS_POOL_POLL_SECONDS,
                        return_when=FIRST_COMPLETED,
                    )
                    for future in done:
                        code = futures[future]
                        try:
                            result = future.result()
                        except Exception as error:
                            result = {"code": code, "error": str(error)}
                        downloaded, completed, last_rows = _record_research_result(
                            result,
                            downloaded,
                            completed,
                            last_rows,
                            failed,
                            progress,
                        )
            except KeyboardInterrupt:
                LOGGER.warning("收到 Ctrl+C，正在停止全部 BaoStock 采集进程")
                _terminate_research_executor(executor)
                raise
            except BaseException:
                executor.shutdown(wait=True, cancel_futures=True)
                raise
            else:
                executor.shutdown(wait=True)
        LOGGER.info(
            "研究数据采集完成：下载=%d；跳过=%d；失败=%d",
            downloaded,
            skipped,
            len(failed),
        )
        return {
            "downloaded": downloaded,
            "skipped": skipped,
            "failed": failed,
            "workers": worker_count,
            "lastRows": last_rows,
        }


def _record_research_result(
    result: dict[str, object],
    downloaded: int,
    completed: int,
    last_rows: int,
    failed: dict[str, str],
    progress: ResearchProgress,
) -> tuple[int, int, int]:
    code = str(result["code"])
    error = str(result.get("error") or "")
    if error:
        failed[code] = error
        LOGGER.warning("研究数据采集失败：股票=%s；原因=%s", code, error)
    else:
        downloaded += 1
        last_rows = max(0, int(result.get("rows") or last_rows))
    completed += 1
    progress.update(completed)
    return downloaded, completed, last_rows


def _initialize_research_worker(
    root: Path,
    proxy_url: str | None,
    progress_completed: object,
    progress_remaining_seconds: object,
    progress_total: int,
    progress_message: object,
) -> None:
    global _WORKER_STORE, _WORKER_PROXY_URL
    signal.signal(signal.SIGINT, signal.SIG_IGN)
    signal.signal(signal.SIGTERM, _handle_worker_termination)
    configure_logging()
    bind_progress_context(
        progress_completed,
        progress_remaining_seconds,
        progress_total,
        progress_message,
    )
    _WORKER_STORE = ParquetMarketStore(root)
    _WORKER_PROXY_URL = proxy_url
    atexit.register(_close_worker_client)
    LOGGER.info("BaoStock 采集进程就绪：进程=%d", os.getpid())


def _handle_worker_termination(signum: int, _frame: object) -> None:
    """收到父进程终止请求时关闭 socket，并立即结束当前 worker。"""
    client = _WORKER_CLIENT
    if client is not None:
        client.interrupt()
    raise SystemExit(128 + signum)


def _terminate_research_executor(executor: ProcessPoolExecutor) -> None:
    """取消排队任务，限时回收 worker，避免中断后遗留孤儿进程。"""
    started_at = time.monotonic()
    processes = []
    forced = 0
    previous_sigint = signal.signal(signal.SIGINT, signal.SIG_IGN)
    try:
        processes = list(
            (executor._processes or {}).values()  # type: ignore[attr-defined]
        )
        manager_thread = executor._executor_manager_thread  # type: ignore[attr-defined]
        executor.shutdown(wait=False, cancel_futures=True)

        for process in processes:
            if process.is_alive():
                process.terminate()

        deadline = time.monotonic() + WORKER_SHUTDOWN_TIMEOUT_SECONDS
        for process in processes:
            remaining = max(0.0, deadline - time.monotonic())
            process.join(remaining)

        for process in processes:
            if not process.is_alive():
                continue
            forced += 1
            process.kill()
            process.join()

        if manager_thread is not None:
            manager_thread.join()
    finally:
        signal.signal(signal.SIGINT, previous_sigint)
    LOGGER.warning(
        "BaoStock 采集进程已全部停止：进程=%d；强制终止=%d；耗时=%.2f 秒",
        len(processes),
        forced,
        time.monotonic() - started_at,
    )


def _sync_research_code(
    code: str, start_date: date, end_date: date
) -> dict[str, object]:
    if _WORKER_STORE is None:
        raise RuntimeError("研究数据采集进程尚未初始化")
    for attempt in range(1, 4):
        try:
            LOGGER.info("正在下载研究数据：股票=%s；尝试=%d/3", code, attempt)
            client = _worker_client()
            daily = client.fetch_daily_history(code, start_date, end_date)
            factors = client.fetch_adjustment_factors(code, date(1990, 1, 1), end_date)
            minutes = client.fetch_minutes(code, start_date, end_date)
            if not daily or not minutes:
                raise ValueError("日线或分钟线为空")
            if not factors:
                factors = [AdjustmentFactor(code, start_date, Decimal("1"))]
            _WORKER_STORE.write_daily_history(code, daily, start_date, end_date)
            _WORKER_STORE.write_adjustment_factors(code, factors, start_date, end_date)
            _WORKER_STORE.write_minute_history(code, minutes, end_date)
            return {"code": code, "error": "", "rows": len(minutes)}
        except (BaoStockError, OSError, TimeoutError) as error:
            _close_worker_client()
            if attempt == 3:
                return {"code": code, "error": str(error)}
            LOGGER.warning(
                "研究数据下载失败，重建会话后重试：股票=%s；尝试=%d/3；原因=%s",
                code,
                attempt,
                error,
            )
            time.sleep(attempt * 3)
        except Exception as error:
            return {"code": code, "error": str(error)}
    return {"code": code, "error": "未知采集失败"}


def _worker_client() -> BaoStockClient:
    global _WORKER_CLIENT
    if _WORKER_CLIENT is None:
        _WORKER_CLIENT = BaoStockClient(_WORKER_PROXY_URL)
        _WORKER_CLIENT.__enter__()
    return _WORKER_CLIENT


def _close_worker_client() -> None:
    global _WORKER_CLIENT
    client = _WORKER_CLIENT
    _WORKER_CLIENT = None
    if client is None:
        return
    try:
        client.__exit__(None, None, None)
    except Exception as error:
        LOGGER.warning("BaoStock 会话关闭失败，连接将由进程回收：%s", error)
# AI_GENERATE_END --------------------
