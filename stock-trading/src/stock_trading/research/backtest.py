# AI_GENERATE_START ----
from __future__ import annotations

import json
import math
from dataclasses import asdict, dataclass
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import pandas as pd

from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.labels import (
    BUY_END,
    BUY_START,
    EXPECTED_BUY_BARS,
    EXPECTED_SELL_BARS,
    SELL_END,
    SELL_START,
)


@dataclass(frozen=True)
class BacktestConfig:
    initial_cash: float = 10_000.0
    min_price: float = 5.0
    max_price: float = 20.0
    commission_rate: float = 0.0003
    min_commission: float = 5.0
    stamp_duty_rate: float = 0.0005
    transfer_fee_rate: float = 0.00001
    slippage_bps: float = 10.0
    lot_size: int = 100
    buy_slices: int = 1
    sell_slices: int = 1
    allowed_prefixes: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if self.initial_cash <= 0 or self.min_price <= 0 or self.max_price < self.min_price:
            raise ValueError("cash and price limits must be positive and ordered")
        if self.lot_size <= 0 or self.buy_slices <= 0 or self.sell_slices <= 0:
            raise ValueError("lot size and order slices must be positive")


@dataclass
class Position:
    instrument: str
    quantity: int
    buy_date: date
    buy_price: float
    buy_notional: float
    buy_fees: float
    candidate_rank: int


class PortfolioBacktester:
    """Single-position T+1 backtest using actual afternoon and next-morning windows."""

    def __init__(
        self,
        market_store: ParquetMarketStore,
        calendar_path: Path,
        topk_path: Path,
        config: BacktestConfig,
    ) -> None:
        self._market_store = market_store
        self._calendar = pd.DatetimeIndex(
            pd.to_datetime(calendar_path.read_text(encoding="utf-8").splitlines())
        )
        self._topk = pd.read_parquet(topk_path).sort_values(
            ["datetime", "score"], ascending=[True, False]
        )
        self._topk["datetime"] = pd.to_datetime(self._topk["datetime"])
        self._config = config
        self._daily: dict[str, pd.DataFrame] = {}
        self._buy_windows: dict[str, pd.DataFrame] = {}
        self._sell_windows: dict[str, pd.DataFrame] = {}

    def run(self, artifact_dir: Path) -> dict[str, object]:
        codes = sorted(self._topk["instrument"].map(_to_baostock_code).unique())
        self._load_market(codes)
        ranked = {
            pd.Timestamp(day): group.reset_index(drop=True)
            for day, group in self._topk.groupby("datetime", sort=True)
        }
        first_prediction = min(ranked)
        last_mature_prediction = self._calendar[-3]
        simulation_dates = self._calendar[
            (self._calendar > first_prediction) & (self._calendar <= self._calendar[-1])
        ]

        cash = self._config.initial_cash
        position: Position | None = None
        trades: list[dict[str, object]] = []
        rejects: list[dict[str, object]] = []
        equity_rows: list[dict[str, object]] = []
        total_fees = 0.0
        total_buy_notional = 0.0
        total_sell_notional = 0.0

        for timestamp in simulation_dates:
            trade_date = timestamp.date()
            if position is not None and trade_date > position.buy_date:
                sale, sell_reject_reason = self._try_sell(position, trade_date)
                if sale is None:
                    rejects.append(
                        _reject_record(
                            trade_date,
                            position.instrument,
                            sell_reject_reason or "SELL_UNAVAILABLE",
                        )
                    )
                else:
                    cash += sale["netProceeds"]
                    total_fees += sale["sellFees"]
                    total_sell_notional += sale["sellNotional"]
                    trades.append(_trade_record(position, sale))
                    position = None

            prediction_timestamp = self._previous_calendar_day(timestamp)
            if (
                position is None
                and prediction_timestamp is not None
                and prediction_timestamp <= last_mature_prediction
            ):
                candidates = ranked.get(prediction_timestamp)
                if candidates is not None:
                    purchase, candidate_rejects = self._try_buy(
                        candidates, prediction_timestamp.date(), trade_date, cash
                    )
                    rejects.extend(candidate_rejects)
                    if purchase is not None:
                        position, cost = purchase
                        cash -= cost
                        total_fees += position.buy_fees
                        total_buy_notional += position.buy_notional

            equity_rows.append(
                {
                    "tradeDate": trade_date.isoformat(),
                    "cash": cash,
                    "marketValue": self._market_value(position, trade_date),
                    "equity": cash + self._market_value(position, trade_date),
                    "instrument": position.instrument if position else None,
                    "quantity": position.quantity if position else 0,
                }
            )

        final_equity = equity_rows[-1]["equity"] if equity_rows else cash
        summary = self._summary(
            trades,
            rejects,
            equity_rows,
            final_equity,
            total_fees,
            total_buy_notional,
            total_sell_notional,
            position,
        )
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
        for code in codes:
            daily = self._market_store.read_daily_history(code).set_index("trade_date")
            minutes = self._market_store.read_minutes(code)
            self._daily[code] = daily
            self._buy_windows[code] = _window_table(
                minutes, BUY_START, BUY_END, EXPECTED_BUY_BARS
            )
            self._sell_windows[code] = _window_table(
                minutes, SELL_START, SELL_END, EXPECTED_SELL_BARS
            )

    def _try_buy(
        self,
        candidates: pd.DataFrame,
        prediction_date: date,
        buy_date: date,
        cash: float,
    ) -> tuple[tuple[Position, float] | None, list[dict[str, object]]]:
        rejects: list[dict[str, object]] = []
        for rank, row in enumerate(candidates.itertuples(index=False), start=1):
            code = _to_baostock_code(row.instrument)
            reason = self._buy_reject_reason(code, prediction_date, buy_date)
            if reason is not None:
                rejects.append(_reject_record(buy_date, row.instrument, reason, rank))
                continue
            window = self._buy_windows[code].loc[pd.Timestamp(buy_date)]
            buy_price = float(window["vwap"]) * (1.0 + self._config.slippage_bps / 10_000.0)
            quantity, fees = self._affordable_quantity(cash, buy_price)
            if quantity <= 0:
                rejects.append(_reject_record(buy_date, row.instrument, "INSUFFICIENT_CASH", rank))
                continue
            notional = buy_price * quantity
            position = Position(
                instrument=row.instrument,
                quantity=quantity,
                buy_date=buy_date,
                buy_price=buy_price,
                buy_notional=notional,
                buy_fees=fees,
                candidate_rank=rank,
            )
            return (position, notional + fees), rejects
        return None, rejects

    def _buy_reject_reason(
        self, code: str, prediction_date: date, buy_date: date
    ) -> str | None:
        if self._config.allowed_prefixes and not any(
            code.startswith(prefix) for prefix in self._config.allowed_prefixes
        ):
            return "NO_TRADING_PERMISSION"
        prediction = self._daily_row(code, prediction_date)
        if prediction is None or str(prediction["tradestatus"]) != "1":
            return "PREDICTION_DAY_UNTRADABLE"
        if str(prediction["is_st"]) == "1":
            return "ST_EXCLUDED"
        if not self._config.min_price <= float(prediction["close"]) <= self._config.max_price:
            return "PRICE_OUT_OF_RANGE"
        buy_daily = self._daily_row(code, buy_date)
        if buy_daily is None or str(buy_daily["tradestatus"]) != "1":
            return "BUY_DAY_SUSPENDED"
        window = self._window_row(self._buy_windows[code], buy_date)
        if window is None:
            return "BUY_WINDOW_INCOMPLETE"
        if _buy_locked_at_limit(code, buy_daily, window):
            return "LIMIT_UP_LOCKED"
        return None

    def _try_sell(
        self, position: Position, sell_date: date
    ) -> tuple[dict[str, float] | None, str | None]:
        code = _to_baostock_code(position.instrument)
        daily = self._daily_row(code, sell_date)
        window = self._window_row(self._sell_windows[code], sell_date)
        if daily is None or str(daily["tradestatus"]) != "1":
            return None, "SELL_DAY_SUSPENDED"
        if window is None:
            return None, "SELL_WINDOW_INCOMPLETE"
        if _sell_locked_at_limit(code, daily, window):
            return None, "LIMIT_DOWN_LOCKED"
        sell_price = float(window["vwap"]) * (1.0 - self._config.slippage_bps / 10_000.0)
        sell_notional = sell_price * position.quantity
        sell_fees = self._sell_fees(sell_notional, position.quantity)
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

    def _affordable_quantity(self, cash: float, price: float) -> tuple[int, float]:
        quantity = math.floor(cash / (price * self._config.lot_size)) * self._config.lot_size
        while quantity > 0:
            fees = self._buy_fees(price * quantity, quantity)
            if price * quantity + fees <= cash:
                return quantity, fees
            quantity -= self._config.lot_size
        return 0, 0.0

    def _buy_fees(self, notional: float, quantity: int) -> float:
        slices = min(self._config.buy_slices, quantity // self._config.lot_size)
        commission = max(notional * self._config.commission_rate, self._config.min_commission * slices)
        return _money(commission + notional * self._config.transfer_fee_rate)

    def _sell_fees(self, notional: float, quantity: int) -> float:
        slices = min(self._config.sell_slices, quantity // self._config.lot_size)
        commission = max(notional * self._config.commission_rate, self._config.min_commission * slices)
        taxes = notional * (self._config.stamp_duty_rate + self._config.transfer_fee_rate)
        return _money(commission + taxes)

    def _market_value(self, position: Position | None, trade_date: date) -> float:
        if position is None:
            return 0.0
        daily = self._daily_row(_to_baostock_code(position.instrument), trade_date)
        return float(daily["close"]) * position.quantity if daily is not None else position.buy_notional

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

    def _summary(
        self,
        trades: list[dict[str, object]],
        rejects: list[dict[str, object]],
        equity_rows: list[dict[str, object]],
        final_equity: float,
        total_fees: float,
        total_buy_notional: float,
        total_sell_notional: float,
        open_position: Position | None,
    ) -> dict[str, object]:
        equity = pd.Series(
            [self._config.initial_cash] + [row["equity"] for row in equity_rows],
            dtype="float64",
        )
        drawdown = equity / equity.cummax() - 1.0 if not equity.empty else pd.Series(dtype=float)
        daily_returns = equity.pct_change().dropna()
        wins = sum(1 for trade in trades if float(trade["netPnl"]) > 0)
        reject_counts = pd.Series([row["reason"] for row in rejects]).value_counts().to_dict()
        gross_profit = final_equity - self._config.initial_cash + total_fees
        return {
            "config": asdict(self._config),
            "initialCash": self._config.initial_cash,
            "finalEquity": _money(final_equity),
            "netProfit": _money(final_equity - self._config.initial_cash),
            "netReturn": final_equity / self._config.initial_cash - 1.0,
            "grossProfitBeforeFees": _money(gross_profit),
            "grossReturnBeforeFees": gross_profit / self._config.initial_cash,
            "feeDrag": total_fees / self._config.initial_cash,
            "closedTrades": len(trades),
            "winRate": wins / len(trades) if trades else 0.0,
            "positiveDailyRate": float((daily_returns > 0).mean()) if not daily_returns.empty else 0.0,
            "maxDrawdown": float(drawdown.min()) if not drawdown.empty else 0.0,
            "totalFees": _money(total_fees),
            "totalBuyNotional": _money(total_buy_notional),
            "totalSellNotional": _money(total_sell_notional),
            "turnover": (total_buy_notional + total_sell_notional) / self._config.initial_cash,
            "openPosition": _position_record(open_position),
            "rejectCounts": reject_counts,
        }


def _window_table(
    minutes: pd.DataFrame,
    start_time,
    end_time,
    expected_bars: int,
) -> pd.DataFrame:
    selected = minutes[
        (minutes["bar_start"].dt.time >= start_time)
        & (minutes["bar_end"].dt.time <= end_time)
    ]
    rows: list[dict[str, object]] = []
    for trade_date, day in selected.groupby("trade_date"):
        if len(day) != expected_bars or day["volume"].sum() <= 0:
            continue
        rows.append(
            {
                "trade_date": pd.Timestamp(trade_date),
                "vwap": day["amount"].sum() / day["volume"].sum(),
                "low": day["low"].min(),
                "high": day["high"].max(),
            }
        )
    if not rows:
        return pd.DataFrame(columns=["vwap", "low", "high"])
    return pd.DataFrame.from_records(rows).set_index("trade_date").sort_index()


def _buy_locked_at_limit(code: str, daily: pd.Series, window: pd.Series) -> bool:
    limit_price = _limit_price(float(daily["preclose"]), _limit_rate(code, daily), 1)
    return float(window["low"]) >= limit_price - 0.001


def _sell_locked_at_limit(code: str, daily: pd.Series, window: pd.Series) -> bool:
    limit_price = _limit_price(float(daily["preclose"]), _limit_rate(code, daily), -1)
    return float(window["high"]) <= limit_price + 0.001


def _limit_rate(code: str, daily: pd.Series) -> float:
    if str(daily["is_st"]) == "1":
        return 0.05
    if code.startswith("sz.300") or code.startswith("sh.688"):
        return 0.20
    return 0.10


def _limit_price(preclose: float, rate: float, direction: int) -> float:
    value = Decimal(str(preclose)) * (Decimal("1") + Decimal(direction) * Decimal(str(rate)))
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def _trade_record(position: Position, sale: dict[str, float]) -> dict[str, object]:
    gross_pnl = sale["sellNotional"] - position.buy_notional
    net_pnl = sale["netProceeds"] - position.buy_notional - position.buy_fees
    return {
        "instrument": position.instrument,
        "candidateRank": position.candidate_rank,
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
        "holdingDays": (sale["sellDate"] - position.buy_date).days,
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
    }


def _to_baostock_code(instrument: str) -> str:
    return f"{instrument[:2].lower()}.{instrument[2:]}"


def _money(value: float) -> float:
    return float(Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
# AI_GENERATE_END ----
