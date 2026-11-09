# AI_GENERATE_START-------- -----------------------------
from __future__ import annotations

import logging
from datetime import date
from decimal import Decimal
from pathlib import Path
from typing import Protocol

from stock_trading.broker.models import (
    BrokerAccount,
    BrokerOrder,
    BrokerPosition,
    FiveLevelQuote,
)
from stock_trading.config import AppConfig
from stock_trading.market.rules import recent_limit_down_count
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.utils.instruments import normalize_instrument, to_broker_code, to_market_code
from stock_trading.state.store import StateStore
from stock_trading.trading.strategy import permission_scope, affordable_quantity
from stock_trading.market.trade_calendar import count_holding_trade_days

LOGGER = logging.getLogger(__name__)


class BrokerReader(Protocol):
    def get_account(self) -> BrokerAccount:
        """用途：读取当前账户资金事实；输入：无；输出：券商账户。"""
        ...

    def get_positions(self) -> tuple[BrokerPosition, ...]:
        """用途：读取当前持仓事实；输入：无；输出：券商持仓元组。"""
        ...

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote:
        """用途：读取证券五档行情；输入：六位证券代码；输出：五档行情。"""
        ...

    def get_today_orders(self) -> tuple[BrokerOrder, ...]:
        """用途：读取当日委托事实；输入：无；输出：券商订单元组。"""
        ...


class LiveTradingOrchestrator:
    """只选择交易目标，不冻结价格、数量或复用旧行情。"""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerReader,
        model_id: str,
        qlib_dir: Path,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker
        self._model_id = model_id
        self._qlib_dir = qlib_dir
        self._market_store = ParquetMarketStore(config.market_dir)

    def select_targets(
        self, business_date: date, side: str
    ) -> dict[str, object]:
        if side == "SELL":
            return self._select_afternoon_sell_targets(business_date)
        if side == "BUY":
            return self._select_afternoon_buy_target(business_date)
        raise ValueError("交易方向必须是 BUY 或 SELL")

    def _select_afternoon_sell_targets(self, business_date: date) -> dict[str, object]:
        """只卖出跌出保留区的 T+1 可卖持仓。"""
        buy_dates = self._unsettled_buy_dates()
        candidates = self._prediction_candidates(business_date)
        ranks = {
            normalize_instrument(item["instrument"]): int(item["rank"])
            for item in candidates
        }
        targets: list[dict[str, object]] = []
        ignored: list[dict[str, object]] = []
        positions = self._broker.get_positions()
        for position in positions:
            instrument = normalize_instrument(position.stock_code, position.market)
            if position.available_quantity <= 0:
                ignored.append(
                    {"instrument": instrument, "reason": "NO_AVAILABLE_QUANTITY"}
                )
                continue
            rank = ranks.get(instrument)
            buy_date = buy_dates.get(instrument)
            holding_days = (
                self._holding_trade_days(buy_date, business_date)
                if buy_date is not None
                else None
            )
            max_holding_reached = (
                holding_days is not None
                and holding_days >= self._config.trading_max_holding_days
            )
            if (
                not max_holding_reached
                and rank is not None
                and rank <= self._config.trading_exit_rank
            ):
                ignored.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "reason": "RANK_RETAINED",
                    }
                )
                continue
            targets.append(
                {
                    "instrument": instrument,
                    "side": "SELL",
                    "candidateRank": rank,
                    "holdingDays": holding_days,
                    "exitReason": (
                        "MAX_HOLDING_DAYS"
                        if max_holding_reached
                        else "RANK_EXIT"
                    ),
                    "modelVersionId": int(candidates[0]["model_version_id"]),
                    "predictionDate": str(candidates[0]["prediction_date"]),
                }
            )
        LOGGER.info(
            "上午 T+1 卖出目标选择完成：目标数=%d；忽略数=%d",
            len(targets),
            len(ignored),
        )
        return {
            "businessDate": business_date.isoformat(),
            "predictionDate": candidates[0]["prediction_date"],
            "mode": "MORNING_SELL",
            "targets": targets,
            "ignored": ignored,
        }

    def _unsettled_buy_dates(self) -> dict[str, date]:
        buy_dates: dict[str, date] = {}
        for order in self._state_store.unsettled_broker_orders():
            if str(order["direction"]) != "BUY":
                continue
            instrument = normalize_instrument(str(order["instrument"]))
            buy_date = date.fromisoformat(str(order["trade_date"]))
            existing = buy_dates.get(instrument)
            if existing is None or buy_date < existing:
                buy_dates[instrument] = buy_date
        return buy_dates

    def _holding_trade_days(self, buy_date: date, business_date: date) -> int:
        return count_holding_trade_days(
            buy_date,
            business_date,
            self._qlib_dir / "calendars" / "day.txt",
        )

    def _select_afternoon_buy_target(self, business_date: date) -> dict[str, object]:
        """从完整横截面排名选择最高分可买目标，不保存五档价格和委托数量。"""
        candidates = self._prediction_candidates(business_date)
        sold_instruments = self._sold_instruments_today(business_date)
        rejected: list[dict[str, object]] = []

        existing_target = self._existing_buy_target(
            business_date, int(candidates[0]["model_version_id"])
        )
        if existing_target is not None:
            if existing_target in sold_instruments:
                rejected.append(
                    {"instrument": existing_target, "reason": "SOLD_SAME_DAY"}
                )
            else:
                candidate = next(
                    (
                        item
                        for item in candidates
                        if normalize_instrument(item["instrument"]) == existing_target
                    ),
                    None,
                )
                LOGGER.info("恢复当日实时买入目标：股票=%s", existing_target)
                return self._buy_result(
                    business_date,
                    candidates,
                    self._target(existing_target, candidate),
                    [],
                )

        positions = tuple(
            position
            for position in self._broker.get_positions()
            if position.quantity > 0
        )
        if positions:
            retained = [
                {
                    "instrument": normalize_instrument(
                        position.stock_code, position.market
                    ),
                    "quantity": position.quantity,
                    "reason": "POSITION_RETAINED",
                }
                for position in positions
            ]
            LOGGER.info("下午买入跳过：仍有持仓=%d", len(retained))
            return self._buy_result(
                business_date, candidates, None, retained
            )

        permission_status = {
            str(item["scope"]): str(item["status"])
            for item in self._state_store.broker_permissions()
        }
        stock_permission_status = {
            str(item["instrument"]): str(item["status"])
            for item in self._state_store.broker_stock_permissions()
        }
        account = self._broker.get_account()
        prediction_date = date.fromisoformat(str(candidates[0]["prediction_date"]))
        for candidate in candidates:
            instrument = normalize_instrument(candidate["instrument"])
            rank = int(candidate["rank"])
            score = float(candidate["score"])
            if instrument in sold_instruments:
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "SOLD_SAME_DAY",
                    }
                )
                continue
            if self._config.trading_allowed_prefixes and not instrument.startswith(
                self._config.trading_allowed_prefixes
            ):
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "NO_TRADING_PERMISSION",
                    }
                )
                continue
            scope = permission_scope(instrument)
            stock_status = stock_permission_status.get(instrument)
            if stock_status == "DENIED" or (
                stock_status is None
                and scope
                and permission_status.get(scope) == "DENIED"
            ):
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "BROKER_PERMISSION_DENIED",
                    }
                )
                continue
            market_code = to_market_code(instrument)
            limit_downs = recent_limit_down_count(
                self._market_store.read_daily_history(market_code),
                market_code,
                prediction_date,
                self._config.trading_limit_down_lookback_days,
            )
            if limit_downs > self._config.trading_max_recent_limit_downs:
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "RECENT_LIMIT_DOWNS",
                    }
                )
                continue
            quote = self._broker.get_five_level_quote(to_broker_code(instrument))
            reference_price = quote.price or quote.asks[0]
            if not Decimal(str(self._config.trading_min_price)) <= reference_price <= Decimal(
                str(self._config.trading_max_price)
            ):
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "PRICE_OUT_OF_RANGE",
                    }
                )
                continue
            quantity = affordable_quantity(
                account.available_cash,
                quote.asks[0],
                Decimal(str(self._config.trading_order_fee)),
            )
            if quantity <= 0:
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "INSUFFICIENT_CASH",
                    }
                )
                continue
            target = self._target(instrument, candidate)
            LOGGER.info(
                "下午买入目标选择完成：股票=%s；全市场排名=%d；评分=%s；可买数量=%d",
                instrument,
                rank,
                score,
                quantity,
            )
            return self._buy_result(business_date, candidates, target, rejected)

        LOGGER.info("下午买入目标选择完成：没有可交易候选")
        return self._buy_result(business_date, candidates, None, rejected)

    def _sold_instruments_today(self, business_date: date) -> set[str]:
        return {
            normalize_instrument(order.stock_code, order.market)
            for order in self._broker.get_today_orders()
            if order.order_time is not None
            and order.order_time.date() == business_date
            and order.direction == "SELL"
            and int(order.filled_quantity or 0) > 0
            and order.stock_code
        }

    def _prediction_candidates(
        self, business_date: date
    ) -> list[dict[str, object]]:
        candidates = self._state_store.prediction_candidates_for_execution(
            business_date.isoformat(), self._model_id
        )
        if not candidates:
            raise ValueError("目标交易日没有匹配前一交易日特征和当前时间合同的横截面预测")
        if len(candidates) <= self._config.trading_entry_rank:
            raise ValueError(
                "最近预测不是完整横截面排名，请先重新执行 stock rank"
            )
        return candidates

    def _existing_buy_target(
        self, business_date: date, model_version_id: int
    ) -> str | None:
        prefix = f"LIVE:AFTERNOON_BUY:{business_date.isoformat()}:"
        matching = [
            row
            for row in self._state_store.list_order_intents()
            if str(row["client_order_id"]).startswith(prefix)
            and row["model_version_id"] == model_version_id
        ]
        if not matching or matching[-1]["status"] == "REJECTED":
            return None
        return normalize_instrument(matching[-1]["instrument"])

    @staticmethod
    def _target(
        instrument: str, candidate: dict[str, object] | None
    ) -> dict[str, object]:
        return {
            "instrument": instrument,
            "side": "BUY",
            "candidateRank": int(candidate["rank"]) if candidate else None,
            "modelScore": float(candidate["score"]) if candidate else None,
            "modelVersionId": int(candidate["model_version_id"]) if candidate else None,
            "predictionDate": str(candidate["prediction_date"]) if candidate else None,
        }

    @staticmethod
    def _buy_result(
        business_date: date,
        candidates: list[dict[str, object]],
        target: dict[str, object] | None,
        rejected: list[dict[str, object]],
    ) -> dict[str, object]:
        return {
            "businessDate": business_date.isoformat(),
            "predictionDate": candidates[0]["prediction_date"],
            "modelStatus": candidates[0]["model_status"],
            "rankingRows": len(candidates),
            "mode": "AFTERNOON_BUY",
            "targets": [target] if target else [],
            "rejected": rejected,
        }
# AI_GENERATE_END-------- -----------------------------
