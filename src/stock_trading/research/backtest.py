# AI_GENERATE_START-3--------- -------------------------------
from __future__ import annotations

import json
import logging
import math
from dataclasses import asdict, dataclass
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pandas as pd

from stock_trading.config import AppConfig, TRADING_TIME_CONTRACT
from stock_trading.market.domain import limit_price, limit_rate, recent_limit_down_count
from stock_trading.market.domain import normalize_instrument, to_market_code, tradable_days
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.transform import require_window_contract
from stock_trading.research.config import ModelSpec, trading_time_contract
from stock_trading.utils.logging_utils import set_log_context, should_log_progress


LOGGER = logging.getLogger(__name__)

# 同一批量实验中的策略只改变回测参数，不改变行情事实；按数据范围和可交易约束缓存只读行情。
_MARKET_CACHE: dict[tuple[object, ...], tuple[dict[str, pd.DataFrame], dict[str, pd.Series], dict[str, pd.DataFrame]]] = {}


@dataclass(frozen=True)
class BacktestConfig:
    initial_cash: float = 10_000.0
    min_price: float = 0.01
    max_price: float = 1_000_000.0
    order_fee: float = 5.0
    slippage_bps: float = 10.0
    lot_size: int = 100
    buy_slices: int = 1
    sell_slices: int = 1
    allowed_prefixes: tuple[str, ...] = ()
    exit_position: int = 5
    exit_fallback_position: int = 20
    rank_grace_days: int = 1
    max_holding_days: int = 5
    limit_down_lookback_days: int = 10
    max_recent_limit_downs: int = 2

    def __post_init__(self) -> None:
        if self.initial_cash <= 0 or self.min_price <= 0 or self.max_price < self.min_price:
            raise ValueError("cash and price limits must be positive and ordered")
        if self.lot_size <= 0 or self.buy_slices <= 0 or self.sell_slices <= 0:
            raise ValueError("lot size and order slices must be positive")
        if self.max_holding_days <= 0:
            raise ValueError("max holding days must be positive")
        if self.rank_grace_days < 0:
            raise ValueError("rank grace days must be non-negative")
        if self.limit_down_lookback_days <= 0 or self.max_recent_limit_downs < 0:
            raise ValueError("limit-down lookback must be positive and threshold non-negative")


@dataclass
class Position:
    instrument: str
    quantity: int
    buy_date: date
    buy_price: float
    buy_notional: float
    buy_fees: float
    candidate_rank: int
    target_holding_days: int = 1
    rank_grace_used: bool = False


class PortfolioBacktester:
    """按上一交易日排名，下午买入并在下一交易日 09:30–11:30 卖出的 T+1 回测。"""

    def __init__(
        self,
        market_store: ParquetMarketStore,
        calendar_path: Path,
        topk_path: Path,
        config: BacktestConfig,
        model_spec: ModelSpec,
    ) -> None:
        self._market_store = market_store
        self._calendar = pd.DatetimeIndex(
            pd.to_datetime(calendar_path.read_text(encoding="utf-8").splitlines())
        )
        self._topk = (
            pd.read_parquet(topk_path)
            .dropna(subset=["score"])
            .sort_values(["datetime", "score"], ascending=[True, False])
        )
        self._topk["datetime"] = pd.to_datetime(self._topk["datetime"])
        self._config = config
        self._model_spec = model_spec
        stocks = self._market_store.read_stock_basics()
        self._out_dates = {
            normalize_instrument(str(row.code)): pd.Timestamp(row.out_date).date()
            for row in stocks.itertuples(index=False)
            if pd.notna(row.out_date)
        }
        self._daily: dict[str, pd.DataFrame] = {}
        self._tradable: dict[str, pd.Series] = {}
        self._windows: dict[str, pd.DataFrame] = {}

    def run(self, artifact_dir: Path) -> dict[str, object]:
        codes = sorted(self._topk["instrument"].map(to_market_code).unique())
        set_log_context(progress="0/-", target="回测行情")
        LOGGER.info("加载回测行情：股票数=%d", len(codes))
        self._load_market(codes)
        ranked = self._tradable_rankings()
        first_prediction = min(ranked)
        last_buy_date = self._calendar[-2]
        simulation_dates = self._calendar[
            (self._calendar >= first_prediction) & (self._calendar <= self._calendar[-1])
        ]

        cash = self._config.initial_cash
        position: Position | None = None
        trades: list[dict[str, object]] = []
        rejects: list[dict[str, object]] = []
        equity_rows: list[dict[str, object]] = []
        realized_gross_profit = 0.0
        realized_net_profit = 0.0
        retained_days = 0

        total_dates = len(simulation_dates)
        set_log_context(progress="0/-", target="逐日模拟")
        LOGGER.info("开始逐日模拟：交易日数=%d", total_dates)
        for index, timestamp in enumerate(simulation_dates, start=1):
            trade_date = timestamp.date()
            prediction_timestamp = self._previous_calendar_day(timestamp)
            candidates = ranked.get(prediction_timestamp)
            sold_instruments: set[str] = set()
            if position is not None and trade_date > position.buy_date:
                holding_days = self._holding_trade_days(
                    position.buy_date, trade_date
                )
                executable = (
                    self._executable_candidates(
                        candidates,
                        prediction_timestamp.date(),
                        trade_date,
                        cash + position.buy_notional,
                        set(),
                    )
                    if candidates is not None
                    else None
                )
                exit_position = (
                    self._candidate_rank(executable, position.instrument)
                    if executable is not None
                    else None
                )
                fixed_horizon = (
                    self._model_spec.strategy.holding_policy
                    == "FIXED_NEXT_TRADING_DAY"
                )
                predicted_horizon_reached = (
                    self._model_spec.data_level == "L2"
                    and "dynamic-holding-value" in self._model_spec.strategy.label_contract
                    and holding_days >= position.target_holding_days
                )
                max_holding_reached = fixed_horizon or predicted_horizon_reached or (
                    holding_days >= self._config.max_holding_days
                )
                if exit_position is not None and exit_position <= self._config.exit_position:
                    position.rank_grace_used = False
                if (
                    not max_holding_reached
                    and exit_position is not None
                    and (
                        exit_position <= self._config.exit_position
                        or (
                            exit_position <= self._config.exit_fallback_position
                            and self._config.rank_grace_days > 0
                            and not position.rank_grace_used
                        )
                    )
                ):
                    if exit_position > self._config.exit_position:
                        position.rank_grace_used = True
                    retained_days += 1
                else:
                    sale, sell_reject_reason = self._try_sell(position, trade_date)
                    if sale is None:
                        rejects.append(_reject_record(trade_date, position.instrument, sell_reject_reason or "SELL_UNAVAILABLE"))
                    else:
                        cash += sale["sellNotional"]
                        if fixed_horizon:
                            exit_reason = "FIXED_HORIZON"
                        elif predicted_horizon_reached:
                            exit_reason = "PREDICTED_HORIZON"
                        elif max_holding_reached:
                            exit_reason = "MAX_HOLDING_DAYS"
                        else:
                            exit_reason = "RANK_EXIT"
                        trade = _trade_record(
                            position,
                            sale,
                            exit_position,
                            holding_days,
                            exit_reason,
                        )
                        trades.append(trade)
                        realized_gross_profit += float(trade["grossPnl"])
                        realized_net_profit += float(trade["netPnl"])
                        sold_instruments.add(position.instrument)
                        position = None

            if (
                position is None
                and timestamp <= last_buy_date
                and prediction_timestamp is not None
                and candidates is not None
            ):
                purchase, candidate_rejects = self._try_buy(
                    candidates,
                    prediction_timestamp.date(),
                    trade_date,
                    cash,
                    sold_instruments,
                )
                rejects.extend(candidate_rejects)
                if purchase is not None:
                    position, notional = purchase
                    cash -= notional

            equity_rows.append(
                {
                    "tradeDate": trade_date.isoformat(),
                    "cash": cash,
                    "marketValue": self._market_value(position, trade_date),
                    "equity": cash + self._market_value(position, trade_date),
                    "realizedEquity": self._config.initial_cash + realized_gross_profit,
                    "realizedGrossEquity": self._config.initial_cash
                    + realized_gross_profit,
                    "realizedNetEquity": self._config.initial_cash
                    + realized_net_profit,
                    "instrument": position.instrument if position else None,
                    "quantity": position.quantity if position else 0,
                }
            )
            if should_log_progress(index, total_dates):
                set_log_context(
                    progress=f"{index}/{total_dates}", target=str(trade_date)
                )
                LOGGER.info(
                    "回测状态：权益=%.2f；已平仓=%d；拒绝=%d",
                    equity_rows[-1]["equity"],
                    len(trades),
                    len(rejects),
                )

        set_log_context(progress="-/-", target="收益汇总")
        LOGGER.info("开始汇总收益、费用和回撤")
        mark_to_market_equity = equity_rows[-1]["equity"] if equity_rows else cash
        monthly_performance = self._monthly_performance(trades, equity_rows)
        summary = self._summary(
            trades,
            rejects,
            equity_rows,
            monthly_performance,
            mark_to_market_equity,
            position,
            retained_days,
        )
        set_log_context(progress="-/-", target=str(artifact_dir))
        LOGGER.info("开始写入回测制品")
        artifact_dir.mkdir(parents=True, exist_ok=True)
        pd.DataFrame.from_records(trades).to_parquet(artifact_dir / "trades.parquet", index=False)
        pd.DataFrame.from_records(rejects).to_parquet(artifact_dir / "rejects.parquet", index=False)
        pd.DataFrame.from_records(equity_rows).to_parquet(
            artifact_dir / "daily_equity.parquet", index=False
        )
        (artifact_dir / "summary.json").write_text(
            json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        return summary

    def _load_market(self, codes: list[str]) -> None:
        cache_key = (
            str(self._market_store._root),
            tuple(codes),
            self._model_spec.data_level,
            self._calendar.min().date().isoformat(),
            self._calendar.max().date().isoformat(),
            self._config.min_price,
            self._config.max_price,
            self._config.limit_down_lookback_days,
            self._config.max_recent_limit_downs,
        )
        cached = _MARKET_CACHE.get(cache_key)
        if cached is not None:
            self._daily, self._tradable, self._windows = cached
            LOGGER.info("复用回测行情缓存：股票数=%d", len(codes))
            return
        total_codes = len(codes)
        for index, code in enumerate(codes, start=1):
            daily = self._market_store.read_daily_history(code).set_index("trade_date")
            volume = pd.to_numeric(daily["volume"], errors="coerce")
            amount = pd.to_numeric(daily["amount"], errors="coerce")
            daily["vwap"] = (amount / volume).where(volume > 0)
            self._daily[code] = daily
            self._tradable[code] = tradable_days(
                daily,
                code,
                self._config.min_price,
                self._config.max_price,
                self._config.limit_down_lookback_days,
                self._config.max_recent_limit_downs,
            )
            if self._model_spec.data_level == "L2":
                self._windows[code] = self._market_store.read_minute_summaries(
                    code, self._calendar.min().date(), self._calendar.max().date()
                ).set_index("trade_date")
                require_window_contract(self._windows[code])
            if should_log_progress(index, total_codes):
                set_log_context(progress=f"{index}/{total_codes}", target=code)
                LOGGER.info("回测行情加载完成")
        _MARKET_CACHE[cache_key] = (self._daily, self._tradable, self._windows)

    def _tradable_rankings(self) -> dict[pd.Timestamp, pd.DataFrame]:
        """先过滤预测日可交易池再按分数排序，排名序号即真实候选顺序。"""
        frame = self._topk.assign(
            instrument=self._topk["instrument"].map(normalize_instrument)
        )
        if self._config.allowed_prefixes:
            frame = frame[
                frame["instrument"].str.startswith(self._config.allowed_prefixes)
            ]
        out_date = frame["instrument"].map(self._out_dates)
        frame = frame[
            ~(
                out_date.notna()
                & (
                    pd.to_datetime(out_date)
                    <= frame["datetime"] + pd.Timedelta(days=30)
                )
            )
        ]
        codes = frame["instrument"].map(to_market_code)
        keep = pd.Series(False, index=frame.index)
        for code, group in frame.groupby(codes, sort=False):
            flags = self._tradable[code].reindex(group["datetime"]).fillna(False)
            keep.loc[group.index] = flags.to_numpy(dtype=bool)
        frame = frame[keep]
        return {
            pd.Timestamp(day): group.sort_values("score", ascending=False)
            .reset_index(drop=True)
            for day, group in frame.groupby("datetime", sort=True)
        }

    def _try_buy(
        self,
        candidates: pd.DataFrame,
        prediction_date: date,
        buy_date: date,
        cash: float,
        excluded_instruments: set[str],
    ) -> tuple[tuple[Position, float] | None, list[dict[str, object]]]:
        rejects: list[dict[str, object]] = []
        for rank, row in enumerate(candidates.itertuples(index=False), start=1):
            instrument = normalize_instrument(row.instrument)
            if instrument in excluded_instruments:
                rejects.append(
                    _reject_record(buy_date, instrument, "SOLD_SAME_DAY", rank)
                )
                continue
            code = to_market_code(instrument)
            reason = self._buy_reject_reason(
                instrument, code, prediction_date, buy_date
            )
            if reason is not None:
                rejects.append(_reject_record(buy_date, instrument, reason, rank))
                continue
            buy_price = self._buy_benchmark(code, buy_date) * (
                1.0 + self._config.slippage_bps / 10_000.0
            )
            quantity, fees = self._affordable_quantity(cash, buy_price)
            if quantity <= 0:
                rejects.append(
                    _reject_record(buy_date, instrument, "INSUFFICIENT_CASH", rank)
                )
                continue
            notional = buy_price * quantity
            position = Position(
                instrument=instrument,
                quantity=quantity,
                buy_date=buy_date,
                buy_price=buy_price,
                buy_notional=notional,
                buy_fees=fees,
                candidate_rank=rank,
                target_holding_days=(
                    self._predicted_holding_days(row)
                    if self._model_spec.data_level == "L2"
                    else 1
                ),
            )
            return (position, notional), rejects
        return None, rejects

    def _executable_candidates(
        self,
        candidates: pd.DataFrame,
        prediction_date: date,
        buy_date: date,
        cash: float,
        excluded_instruments: set[str],
    ) -> pd.DataFrame:
        """按实际买入约束过滤后返回可执行排名，供持仓保留判断使用。"""
        rows: list[dict[str, object]] = []
        for row in candidates.itertuples(index=False):
            instrument = normalize_instrument(row.instrument)
            if instrument in excluded_instruments:
                continue
            code = to_market_code(instrument)
            if self._buy_reject_reason(instrument, code, prediction_date, buy_date):
                continue
            buy_price = self._buy_benchmark(code, buy_date) * (
                1.0 + self._config.slippage_bps / 10_000.0
            )
            quantity, _ = self._affordable_quantity(cash, buy_price)
            if quantity <= 0:
                continue
            rows.append({key: getattr(row, key) for key in row._fields})
        return pd.DataFrame.from_records(rows, columns=list(candidates.columns))

    @staticmethod
    def _predicted_holding_days(row: object) -> int:
        values = [
            float(getattr(row, f"score_h{horizon}", float("nan")))
            for horizon in (1, 2, 3)
        ]
        valid = [index for index, value in enumerate(values, start=1) if pd.notna(value)]
        return max(valid, key=lambda index: values[index - 1]) if valid else 1

    @staticmethod
    def _candidate_rank(
        candidates: pd.DataFrame, instrument: str
    ) -> int | None:
        normalized = normalize_instrument(instrument)
        for rank, row in enumerate(candidates.itertuples(index=False), start=1):
            if normalize_instrument(row.instrument) == normalized:
                return rank
        return None

    def _buy_reject_reason(
        self,
        instrument: str,
        code: str,
        prediction_date: date,
        buy_date: date,
    ) -> str | None:
        if self._config.allowed_prefixes and not any(
            instrument.startswith(prefix) for prefix in self._config.allowed_prefixes
        ):
            return "NO_TRADING_PERMISSION"
        out_date = self._out_dates.get(instrument)
        if out_date is not None and out_date <= buy_date + timedelta(days=30):
            return "DELISTING_SOON"
        prediction = self._daily_row(code, prediction_date)
        if prediction is None:
            return "PREDICTION_DAY_DATA_MISSING"
        if str(prediction["tradestatus"]) != "1":
            return "PREDICTION_DAY_UNTRADABLE"
        if str(prediction["is_st"]) == "1":
            return "ST_EXCLUDED"
        if recent_limit_down_count(
            self._daily[code],
            code,
            prediction_date,
            self._config.limit_down_lookback_days,
        ) > self._config.max_recent_limit_downs:
            return "RECENT_LIMIT_DOWNS"
        if not self._config.min_price <= float(prediction["close"]) <= self._config.max_price:
            return "PRICE_OUT_OF_RANGE"
        buy_daily = self._daily_row(code, buy_date)
        if buy_daily is None:
            return "BUY_DAY_DATA_MISSING"
        if str(buy_daily["tradestatus"]) != "1":
            return "BUY_DAY_SUSPENDED"
        if self._model_spec.strategy.price_benchmark == "window-vwap":
            window = self._window_row(self._windows[code], buy_date)
            if window is None or not bool(window["buy_complete"]):
                return "BUY_WINDOW_INCOMPLETE"
            if _buy_locked_at_limit(code, buy_daily, window):
                return "LIMIT_UP_LOCKED"
        elif not float(buy_daily["vwap"]) > 0:
            return "BUY_DAILY_VWAP_MISSING"
        return None

    def _try_sell(
        self, position: Position, sell_date: date
    ) -> tuple[dict[str, float] | None, str | None]:
        code = to_market_code(position.instrument)
        daily = self._daily_row(code, sell_date)
        if daily is None:
            return None, "SELL_DAY_DATA_MISSING"
        if str(daily["tradestatus"]) != "1":
            return None, "SELL_DAY_SUSPENDED"
        if self._model_spec.strategy.price_benchmark == "window-vwap":
            window = self._window_row(self._windows[code], sell_date)
            if window is None or not bool(window["sell_complete"]):
                return None, "SELL_WINDOW_INCOMPLETE"
            if _sell_locked_at_limit(code, daily, window):
                return None, "LIMIT_DOWN_LOCKED"
            benchmark = float(window["sell_vwap"])
        else:
            benchmark = float(daily["vwap"])
            if not benchmark > 0:
                return None, "SELL_DAILY_VWAP_MISSING"
        sell_price = benchmark * (
            1.0 - self._config.slippage_bps / 10_000.0
        )
        sell_notional = sell_price * position.quantity
        sell_fees = self._sell_fees(position.quantity)
        return (
            {
                "sellDate": sell_date,
                "sellPrice": sell_price,
                "sellNotional": sell_notional,
                "sellFees": sell_fees,
                "netProceeds": sell_notional - sell_fees,
            },
            None,
        )

    def _buy_benchmark(self, code: str, buy_date: date) -> float:
        if self._model_spec.strategy.price_benchmark == "daily-vwap":
            return float(self._daily_row(code, buy_date)["vwap"])
        return float(self._window_row(self._windows[code], buy_date)["buy_vwap"])

    def _affordable_quantity(self, cash: float, price: float) -> tuple[int, float]:
        quantity = math.floor(cash / (price * self._config.lot_size)) * self._config.lot_size
        fees = self._buy_fees(quantity) if quantity > 0 else 0.0
        return quantity, fees

    def _buy_fees(self, quantity: int) -> float:
        return self._order_fees(quantity, self._config.buy_slices)

    def _sell_fees(self, quantity: int) -> float:
        return self._order_fees(quantity, self._config.sell_slices)

    def _order_fees(self, quantity: int, max_slices: int) -> float:
        slices = min(max_slices, quantity // self._config.lot_size)
        return _money(self._config.order_fee * slices)

    def _market_value(self, position: Position | None, trade_date: date) -> float:
        if position is None:
            return 0.0
        history = self._daily[to_market_code(position.instrument)]
        available = history.loc[history.index <= pd.Timestamp(trade_date)]
        if available.empty:
            return position.buy_notional
        return float(available.iloc[-1]["close"]) * position.quantity

    def _daily_row(self, code: str, trade_date: date) -> pd.Series | None:
        return self._window_row(self._daily[code], trade_date)

    @staticmethod
    def _window_row(frame: pd.DataFrame, trade_date: date) -> pd.Series | None:
        timestamp = pd.Timestamp(trade_date)
        if timestamp not in frame.index:
            return None
        row = frame.loc[timestamp]
        return row.iloc[0] if isinstance(row, pd.DataFrame) else row

    def _previous_calendar_day(self, timestamp: pd.Timestamp) -> pd.Timestamp | None:
        index = self._calendar.get_loc(timestamp)
        return self._calendar[index - 1] if index > 0 else None

    def _holding_trade_days(self, buy_date: date, trade_date: date) -> int:
        return int(
            (
                (self._calendar > pd.Timestamp(buy_date))
                & (self._calendar <= pd.Timestamp(trade_date))
            ).sum()
        )

    def _summary(
        self,
        trades: list[dict[str, object]],
        rejects: list[dict[str, object]],
        equity_rows: list[dict[str, object]],
        monthly_performance: list[dict[str, object]],
        mark_to_market_equity: float,
        open_position: Position | None,
        retained_days: int,
    ) -> dict[str, object]:
        total_fees = sum(
            float(trade["buyFees"]) + float(trade["sellFees"])
            for trade in trades
        )
        total_buy_notional = sum(float(trade["buyNotional"]) for trade in trades)
        total_sell_notional = sum(float(trade["sellNotional"]) for trade in trades)
        net_profit = sum(float(trade["netPnl"]) for trade in trades)
        gross_profit = sum(float(trade["grossPnl"]) for trade in trades)
        gross_equity = pd.Series(
            [self._config.initial_cash]
            + [row["realizedGrossEquity"] for row in equity_rows],
            dtype="float64",
        )
        net_equity = pd.Series(
            [self._config.initial_cash]
            + [row["realizedNetEquity"] for row in equity_rows],
            dtype="float64",
        )
        gross_drawdown = (
            gross_equity / gross_equity.cummax() - 1.0
            if not gross_equity.empty
            else pd.Series(dtype=float)
        )
        net_drawdown = (
            net_equity / net_equity.cummax() - 1.0
            if not net_equity.empty
            else pd.Series(dtype=float)
        )
        daily_returns = gross_equity.pct_change().dropna()
        wins = sum(1 for trade in trades if float(trade["grossPnl"]) > 0)
        reject_counts = pd.Series([row["reason"] for row in rejects]).value_counts().to_dict()
        return {
            "config": asdict(self._config),
            "timeContractVersion": TRADING_TIME_CONTRACT,
            "timeContract": trading_time_contract(self._model_spec),
            "simulationType": "window-vwap-benchmark",
            "sameDayRebuyAllowed": False,
            "buyCandidateSelection": "EXECUTABLE_POOL_RANKED",
            "executionLimitations": (
                "窗口VWAP基准，并非逐分钟成交回放；不模拟盘口排队、部分成交和撤单。"
            ),
            "initialCash": self._config.initial_cash,
            "finalEquity": _money(self._config.initial_cash + gross_profit),
            "realizedGrossEquity": _money(self._config.initial_cash + gross_profit),
            "realizedNetEquity": _money(self._config.initial_cash + net_profit),
            "markToMarketEquity": _money(mark_to_market_equity),
            "netProfit": _money(net_profit),
            "netReturn": net_profit / self._config.initial_cash,
            "grossProfitBeforeFees": _money(gross_profit),
            "grossReturnBeforeFees": gross_profit / self._config.initial_cash,
            "feeDrag": total_fees / self._config.initial_cash,
            "closedTrades": len(trades),
            "retainedDays": retained_days,
            "averageHoldingDays": (
                sum(int(trade["holdingDays"]) for trade in trades) / len(trades)
                if trades
                else 0.0
            ),
            "winRate": wins / len(trades) if trades else 0.0,
            "positiveDailyRate": float((daily_returns > 0).mean()) if not daily_returns.empty else 0.0,
            "maxDrawdown": float(gross_drawdown.min()) if not gross_drawdown.empty else 0.0,
            "netMaxDrawdown": float(net_drawdown.min()) if not net_drawdown.empty else 0.0,
            "totalFees": _money(total_fees),
            "totalBuyNotional": _money(total_buy_notional),
            "totalSellNotional": _money(total_sell_notional),
            "turnover": (total_buy_notional + total_sell_notional) / self._config.initial_cash,
            "monthlyPerformance": monthly_performance,
            "monthlyReturnStability": _monthly_return_stability(monthly_performance),
            "openPosition": _position_record(open_position),
            "rejectCounts": reject_counts,
        }

    def _monthly_performance(
        self,
        trades: list[dict[str, object]],
        equity_rows: list[dict[str, object]],
    ) -> list[dict[str, object]]:
        if not equity_rows:
            return []
        trade_frame = pd.DataFrame.from_records(trades)
        if not trade_frame.empty:
            trade_frame["month"] = pd.to_datetime(trade_frame["sellDate"]).dt.to_period("M")
        first_month = pd.Timestamp(equity_rows[0]["tradeDate"]).to_period("M")
        last_month = pd.Timestamp(equity_rows[-1]["tradeDate"]).to_period("M")
        gross_start_equity = self._config.initial_cash
        net_start_equity = self._config.initial_cash
        rows: list[dict[str, object]] = []
        for month in pd.period_range(first_month, last_month, freq="M"):
            month_trades = (
                trade_frame.loc[trade_frame["month"] == month]
                if not trade_frame.empty
                else trade_frame
            )
            gross_profit = float(month_trades["grossPnl"].sum()) if not month_trades.empty else 0.0
            net_profit = float(month_trades["netPnl"].sum()) if not month_trades.empty else 0.0
            closed_trades = len(month_trades)
            wins = (
                int((pd.to_numeric(month_trades["grossPnl"]) > 0).sum())
                if closed_trades
                else 0
            )
            rows.append(
                {
                    "month": str(month),
                    "startGrossEquity": _money(gross_start_equity),
                    "grossProfit": _money(gross_profit),
                    "grossReturn": gross_profit / gross_start_equity,
                    "endGrossEquity": _money(gross_start_equity + gross_profit),
                    "startNetEquity": _money(net_start_equity),
                    "netProfit": _money(net_profit),
                    "netReturn": net_profit / net_start_equity,
                    "endNetEquity": _money(net_start_equity + net_profit),
                    "closedTrades": closed_trades,
                    "winRate": wins / closed_trades if closed_trades else 0.0,
                }
            )
            gross_start_equity += gross_profit
            net_start_equity += net_profit
        return rows


def run_portfolio_backtest(
    config: AppConfig,
    spec: ModelSpec,
    model_dir: Path,
    artifact_dir: Path,
    initial_cash: Decimal | int | float,
    qlib_dir: Path | None = None,
    exit_fallback_position: int = 20,
    rank_grace_days: int = 1,
) -> dict[str, object]:
    """使用统一交易配置执行一个资金档位的组合回测。"""
    backtest_config = BacktestConfig(
        initial_cash=float(initial_cash),
        min_price=config.trading_min_price,
        max_price=config.trading_max_price,
        order_fee=config.trading_order_fee,
        allowed_prefixes=config.trading_allowed_prefixes,
        exit_position=config.exit_position,
        exit_fallback_position=exit_fallback_position,
        rank_grace_days=rank_grace_days,
        max_holding_days=config.trading_max_holding_days,
        limit_down_lookback_days=config.trading_limit_down_lookback_days,
        max_recent_limit_downs=config.trading_max_recent_limit_downs,
    )
    return PortfolioBacktester(
        ParquetMarketStore(config.market_dir),
        (qlib_dir or config.prepared_dir) / "calendars" / "day.txt",
        model_dir / "test_predictions.parquet",
        backtest_config,
        model_spec=spec,
    ).run(artifact_dir)


def _buy_locked_at_limit(code: str, daily: pd.Series, window: pd.Series) -> bool:
    upper_limit = limit_price(float(daily["preclose"]), limit_rate(code, daily), 1)
    return float(window["buy_low"]) >= upper_limit - 0.001


def _sell_locked_at_limit(code: str, daily: pd.Series, window: pd.Series) -> bool:
    lower_limit = limit_price(float(daily["preclose"]), limit_rate(code, daily), -1)
    return float(window["sell_high"]) <= lower_limit + 0.001


def _trade_record(
    position: Position,
    sale: dict[str, float],
    exit_position: int | None,
    holding_days: int,
    exit_reason: str,
) -> dict[str, object]:
    gross_pnl = sale["sellNotional"] - position.buy_notional
    net_pnl = sale["netProceeds"] - position.buy_notional - position.buy_fees
    return {
        "instrument": position.instrument,
        "candidateRank": position.candidate_rank,
        "exitPosition": exit_position,
        "exitReason": exit_reason,
        "buyDate": position.buy_date.isoformat(),
        "sellDate": sale["sellDate"].isoformat(),
        "quantity": position.quantity,
        "buyPrice": position.buy_price,
        "sellPrice": sale["sellPrice"],
        "buyNotional": position.buy_notional,
        "sellNotional": sale["sellNotional"],
        "buyFees": position.buy_fees,
        "sellFees": sale["sellFees"],
        "grossPnl": gross_pnl,
        "netPnl": net_pnl,
        "grossReturn": gross_pnl / position.buy_notional,
        "netReturn": net_pnl / (position.buy_notional + position.buy_fees),
        "holdingDays": holding_days,
    }


def _reject_record(
    trade_date: date, instrument: str, reason: str, candidate_rank: int | None = None
) -> dict[str, object]:
    return {
        "tradeDate": trade_date.isoformat(),
        "instrument": instrument,
        "candidateRank": candidate_rank,
        "reason": reason,
    }


def _position_record(position: Position | None) -> dict[str, object] | None:
    if position is None:
        return None
    return {
        "instrument": position.instrument,
        "quantity": position.quantity,
        "buyDate": position.buy_date.isoformat(),
        "buyPrice": position.buy_price,
        "buyNotional": position.buy_notional,
        "buyFees": position.buy_fees,
        "candidateRank": position.candidate_rank,
        "targetHoldingDays": position.target_holding_days,
    }


def _money(value: float) -> float:
    return float(Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def _monthly_return_stability(
    monthly_performance: list[dict[str, object]],
) -> dict[str, object]:
    returns = pd.Series(
        [float(row["grossReturn"]) for row in monthly_performance],
        dtype="float64",
    )
    if returns.empty:
        return {
            "score": 0.0,
            "averageMonthlyReturn": 0.0,
            "monthlyReturnVolatility": 0.0,
            "positiveMonthRate": 0.0,
            "maxConsecutiveLossMonths": 0,
            "bestMonth": None,
            "worstMonth": None,
            "definition": "平均月毛收益率 / 月毛收益率总体标准差",
        }
    average_return = float(returns.mean())
    volatility = float(returns.std(ddof=0))
    loss_streak = 0
    max_loss_streak = 0
    for value in returns:
        loss_streak = loss_streak + 1 if value < 0 else 0
        max_loss_streak = max(max_loss_streak, loss_streak)
    best_index = int(returns.idxmax())
    worst_index = int(returns.idxmin())
    return {
        "score": average_return / volatility if volatility > 0 else 0.0,
        "averageMonthlyReturn": average_return,
        "monthlyReturnVolatility": volatility,
        "positiveMonthRate": float((returns > 0).mean()),
        "maxConsecutiveLossMonths": max_loss_streak,
        "bestMonth": monthly_performance[best_index]["month"],
        "worstMonth": monthly_performance[worst_index]["month"],
        "definition": "平均月毛收益率 / 月毛收益率总体标准差",
    }


def backtest_artifact_valid(
    directory: Path,
    *,
    initial_cash: float | None = None,
    exit_position: int | None = None,
    max_holding_days: int | None = None,
    limit_down_lookback_days: int | None = None,
    max_recent_limit_downs: int | None = None,
) -> bool:
    """检查回测摘要、交易、拒绝和权益制品是否完整。"""
    required = (
        directory / "summary.json",
        directory / "trades.parquet",
        directory / "rejects.parquet",
        directory / "daily_equity.parquet",
    )
    if not all(path.is_file() for path in required):
        return False
    try:
        summary = read_backtest_summary(directory)
        equity = pd.read_parquet(directory / "daily_equity.parquet")
    except (OSError, ValueError, json.JSONDecodeError):
        return False
    required_summary = {
        "grossReturnBeforeFees",
        "realizedGrossEquity",
        "realizedNetEquity",
        "retainedDays",
        "averageHoldingDays",
        "netMaxDrawdown",
        "monthlyPerformance",
        "monthlyReturnStability",
        "sameDayRebuyAllowed",
        "buyCandidateSelection",
    }
    required_equity = {"realizedGrossEquity", "realizedNetEquity"}
    config = summary.get("config")
    if (
        summary.get("timeContractVersion") != TRADING_TIME_CONTRACT
        or summary.get("simulationType") != "window-vwap-benchmark"
        or summary.get("sameDayRebuyAllowed") is not False
        or summary.get("buyCandidateSelection")
        != "EXECUTABLE_POOL_RANKED"
        or not required_summary.issubset(summary)
        or not required_equity.issubset(equity.columns)
        or not isinstance(config, dict)
        or equity.empty
    ):
        return False
    if initial_cash is not None and float(summary.get("initialCash", 0)) != float(
        initial_cash
    ):
        return False
    if exit_position is not None and int(config.get("exit_position", 0)) != exit_position:
        return False
    if (
        max_holding_days is not None
        and int(config.get("max_holding_days", 0)) != max_holding_days
    ):
        return False
    if (
        limit_down_lookback_days is not None
        and int(config.get("limit_down_lookback_days", 0))
        != limit_down_lookback_days
    ):
        return False
    if (
        max_recent_limit_downs is not None
        and int(config.get("max_recent_limit_downs", -1))
        != max_recent_limit_downs
    ):
        return False
    return True


def read_backtest_summary(directory: Path) -> dict[str, object]:
    """读取模型回测摘要。"""
    return json.loads((directory / "summary.json").read_text(encoding="utf-8"))

# AI_GENERATE_END-3--------- -------------------------------
