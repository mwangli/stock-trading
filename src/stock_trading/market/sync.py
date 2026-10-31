# AI_GENERATE_START --
from __future__ import annotations

import logging
import time
from datetime import date, timedelta
from decimal import Decimal

import pandas as pd

from stock_trading.logging_utils import should_log_progress
from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.models import AdjustmentFactor, DailyBar, MinuteBar
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
    """按主板代码、上市区间和交易状态生成研究股票池。"""
    codes: list[str] = []
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(("sh.60", "sz.00")):
            continue
        ipo_date = pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        out_date = pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        if ipo_date and ipo_date > start_date:
            continue
        if out_date and out_date < end_date:
            continue
        if str(row.status) not in {"", "1"} or str(row.trade_status) != "1":
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
) -> dict[str, object]:
    """幂等同步研究股票的日线、复权因子、分钟线和分钟聚合结果。"""
    skipped = 0
    downloaded = 0
    failed: dict[str, str] = {}
    for index, code in enumerate(codes, start=1):
        if store.research_data_covers(code, start_date, end_date):
            skipped += 1
        else:
            try:
                daily, factors, minutes = fetch_research_data(
                    proxy_url, code, start_date, end_date
                )
                if not daily or not minutes:
                    raise ValueError("日线或分钟线为空")
                store.write_daily_history(code, daily, start_date, end_date)
                store.write_adjustment_factors(code, factors, start_date, end_date)
                store.write_minute_history(code, minutes, end_date)
                downloaded += 1
            except Exception as error:
                failed[code] = str(error)
                LOGGER.warning("研究数据采集失败：股票=%s；原因=%s", code, error)
        if should_log_progress(index, len(codes)):
            LOGGER.info(
                "研究数据同步进度：%d/%d；下载=%d；跳过=%d；失败=%d",
                index,
                len(codes),
                downloaded,
                skipped,
                len(failed),
            )
    return {"downloaded": downloaded, "skipped": skipped, "failed": failed}


def fetch_research_data(
    proxy_url: str | None, code: str, start_date: date, end_date: date
) -> tuple[list[DailyBar], list[AdjustmentFactor], list[MinuteBar]]:
    """下载一只股票的研究行情，并对临时网络错误最多重试三次。"""
    for attempt in range(1, 4):
        try:
            LOGGER.info("正在下载研究数据：股票=%s；尝试=%d/3", code, attempt)
            with BaoStockClient(proxy_url) as client:
                daily = client.fetch_daily_history(code, start_date, end_date)
                factors = client.fetch_adjustment_factors(
                    code, date(1990, 1, 1), end_date
                )
                minutes = client.fetch_minutes(code, start_date, end_date)
            if not factors:
                factors = [AdjustmentFactor(code, start_date, Decimal("1"))]
            return daily, factors, minutes
        except (BaoStockError, OSError, TimeoutError) as error:
            if attempt == 3:
                raise
            LOGGER.warning(
                "研究数据下载失败，准备重试：股票=%s；尝试=%d/3；原因=%s",
                code,
                attempt,
                error,
            )
            time.sleep(attempt * 3)
    raise RuntimeError(f"无法完成研究数据下载: {code}")
# AI_GENERATE_END --
