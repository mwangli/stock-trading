# AI_GENERATE_START ----
from __future__ import annotations

import logging
from collections.abc import Mapping
from datetime import date
from decimal import Decimal

import pandas as pd

from stock_trading.market.models import (
    BASIC_CONTRACT_BAOSTOCK,
    FACTOR_CONTRACT_BAOSTOCK,
    CumulativeAdjustmentFactor,
    DailyBasic,
)
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.utils.logging_utils import set_log_context, should_log_progress

LOGGER = logging.getLogger(__name__)


def prepare_daily_derivatives(
    store: ParquetMarketStore,
    codes: list[str],
    start_date: date,
    end_date: date,
    start_dates: Mapping[str, date] | None = None,
    end_dates: Mapping[str, date] | None = None,
) -> dict[str, object]:
    """从本地日线一次生成 basic 与 factor，不访问外部数据源。"""
    basic_ready = set(
        store.valid_basic_codes(
            codes,
            start_date,
            end_date,
            63,
            start_dates=start_dates,
            end_dates=end_dates,
        )
    )
    factor_ready = {
        code
        for code in codes
        if store.factors_valid(
            code,
            (end_dates or {}).get(code, end_date),
            (start_dates or {}).get(code, start_date),
        )
    }
    pending = [
        code
        for code in codes
        if code not in basic_ready or code not in factor_ready
    ]
    basic_codes = 0
    basic_rows = 0
    factor_codes = 0
    factor_rows = 0
    failures: dict[str, str] = {}
    for index, code in enumerate(pending, start=1):
        set_log_context(progress=f"{index}/{len(pending)}", target=code)
        if should_log_progress(index, len(pending)):
            LOGGER.info(
                "日线派生进度：股票=%s；进度=%d/%d",
                code,
                index,
                len(pending),
            )
        try:
            daily = store.read_daily_history(code)
            code_start = (start_dates or {}).get(code, start_date)
            code_end = (end_dates or {}).get(code, end_date)
            if code not in basic_ready:
                basics = _derive_basics(code, daily, code_start, code_end)
                if basics:
                    store.write_basic_history(code, basics)
                    basic_codes += 1
                    basic_rows += len(basics)
            if code not in factor_ready:
                factors = _derive_factors(code, daily)
                if factors:
                    store.write_factors(code, factors)
                    factor_codes += 1
                    factor_rows += len(factors)
        except Exception as error:
            failures[code] = str(error)
            LOGGER.warning("日线派生失败：股票=%s；原因=%s", code, error)
    return {
        "requestedCodes": len(codes),
        "pendingCodes": len(pending),
        "basicCodes": basic_codes,
        "basicRows": basic_rows,
        "factorCodes": factor_codes,
        "factorRows": factor_rows,
        "failedCodes": len(failures),
        "failureSamples": dict(list(failures.items())[:20]),
    }


def transfer_daily_source(
    raw_store: ParquetMarketStore,
    derived_store: ParquetMarketStore,
    codes: list[str],
    start_dates: Mapping[str, date],
    end_dates: Mapping[str, date],
) -> dict[str, object]:
    """将通过质量检查的原始日线物化到 derived，供 prepare 独立读取。"""
    copied = 0
    failed: dict[str, str] = {}
    for code in codes:
        try:
            daily = raw_store.read_daily_history(code)
            if not raw_store.daily_history_valid(
                code, start_dates[code], end_dates[code]
            ):
                continue
            derived_store.write_derived_daily_history(code, daily)
            copied += 1
        except Exception as error:
            failed[code] = str(error)
    return {
        "requestedCodes": len(codes),
        "copiedCodes": copied,
        "failedCodes": len(failed),
        "failureSamples": dict(list(failed.items())[:20]),
    }


def _derive_factors(
    code: str, daily: pd.DataFrame
) -> list[CumulativeAdjustmentFactor]:
    if daily.empty:
        return []
    ordered = daily.sort_values("trade_date")
    cumulative = Decimal("1")
    previous_close: Decimal | None = None
    factors: list[CumulativeAdjustmentFactor] = []
    for row in ordered.itertuples(index=False):
        close = Decimal(str(row.close))
        preclose = Decimal(str(row.preclose))
        if previous_close is not None and preclose > 0:
            cumulative *= previous_close / preclose
        factors.append(
            CumulativeAdjustmentFactor(
                code=code,
                operate_date=pd.Timestamp(row.trade_date).date(),
                cumulative_factor=cumulative,
                factor_contract=FACTOR_CONTRACT_BAOSTOCK,
            )
        )
        previous_close = close
    return factors


def _derive_basics(
    code: str,
    daily: pd.DataFrame,
    start_date: date,
    end_date: date,
) -> list[DailyBasic]:
    if daily.empty:
        return []
    ordered = daily.sort_values("trade_date").copy()
    ordered["trade_date"] = pd.to_datetime(
        ordered["trade_date"], errors="raise"
    )
    volume = pd.to_numeric(ordered["volume"], errors="coerce")
    previous_average = volume.shift(1).rolling(5, min_periods=5).mean()
    ordered["volume_ratio"] = volume / previous_average.where(
        previous_average.ne(0)
    )
    dates = ordered["trade_date"].dt.date
    selected = ordered[dates.between(start_date, end_date)]
    values: list[DailyBasic] = []
    for row in selected.itertuples(index=False):
        turnover = _decimal_or_none(row.turnover_rate)
        close = _decimal_or_none(row.close)
        row_volume = _decimal_or_none(row.volume)
        circulating = (
            close * row_volume * Decimal("100") / turnover
            if close is not None
            and row_volume is not None
            and turnover is not None
            and turnover > 0
            else None
        )
        values.append(
            DailyBasic(
                trade_date=pd.Timestamp(row.trade_date).date(),
                code=code,
                turnover_rate=turnover,
                volume_ratio=_decimal_or_none(row.volume_ratio),
                circulating_market_value=circulating,
                pe_ttm=_decimal_or_none(row.pe_ttm),
                pb_mrq=_decimal_or_none(row.pb_mrq),
                ps_ttm=_decimal_or_none(row.ps_ttm),
                pcf_ncf_ttm=_decimal_or_none(row.pcf_ncf_ttm),
                source_contract=BASIC_CONTRACT_BAOSTOCK,
            )
        )
    return values


def _decimal_or_none(value: object) -> Decimal | None:
    if value is None or pd.isna(value):
        return None
    return Decimal(str(value))


# AI_GENERATE_END ----
