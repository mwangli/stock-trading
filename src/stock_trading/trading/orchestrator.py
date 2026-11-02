# AI_GENERATE_START -------------------
from __future__ import annotations

import logging
from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.models import BrokerAccount, BrokerPosition, FiveLevelQuote
from stock_trading.config import AppConfig
from stock_trading.utils.instruments import normalize_instrument, to_broker_code
from stock_trading.state.store import StateStore
from stock_trading.trading.permissions import permission_scope
from stock_trading.trading.strategy import affordable_quantity

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


class LiveTradingOrchestrator:
    """只选择交易目标，不冻结价格、数量或复用旧行情。"""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerReader,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def select_targets(
        self, business_date: date, side: str
    ) -> dict[str, object]:
        if side == "SELL":
            return self._select_morning_sell_targets(business_date)
        if side == "BUY":
            return self._select_afternoon_buy_target(business_date)
        raise ValueError("交易方向必须是 BUY 或 SELL")

    def _select_morning_sell_targets(self, business_date: date) -> dict[str, object]:
        """只卖出跌出保留排名的可卖持仓，实际数量和价格在委托前重算。"""
        candidates = self._prediction_candidates(business_date)
        candidate_by_instrument = {
            normalize_instrument(candidate["instrument"]): candidate
            for candidate in candidates
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
            candidate = candidate_by_instrument.get(instrument)
            rank = int(candidate["rank"]) if candidate is not None else None
            if rank is not None and rank <= self._config.trading_exit_rank:
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
                    "modelVersionId": (
                        int(candidate["model_version_id"])
                        if candidate is not None
                        else None
                    ),
                    "predictionDate": candidates[0]["prediction_date"],
                }
            )
        LOGGER.info(
            "上午卖出目标选择完成：目标数=%d；忽略数=%d",
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

    def _select_afternoon_buy_target(self, business_date: date) -> dict[str, object]:
        """从完整横截面排名选择最高分可买目标，不保存五档价格和委托数量。"""
        candidates = self._prediction_candidates(business_date)

        existing_target = self._existing_buy_target(business_date)
        if existing_target is not None:
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
        account = self._broker.get_account()
        rejected: list[dict[str, object]] = []
        for candidate in candidates:
            instrument = normalize_instrument(candidate["instrument"])
            rank = int(candidate["rank"])
            score = float(candidate["score"])
            if rank > self._config.trading_entry_rank:
                break
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
            if scope and permission_status.get(scope) == "DENIED":
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "score": score,
                        "reason": "BROKER_PERMISSION_DENIED",
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

    def _prediction_candidates(
        self, business_date: date
    ) -> list[dict[str, object]]:
        candidates = self._state_store.latest_prediction_candidates(
            business_date.isoformat()
        )
        if not candidates:
            raise ValueError("目标交易日前没有可用横截面预测")
        if len(candidates) <= self._config.trading_entry_rank:
            raise ValueError("最近预测不是完整横截面排名，请先重新执行 stock-rank")
        prediction_date = date.fromisoformat(str(candidates[0]["prediction_date"]))
        prediction_age = (business_date - prediction_date).days
        if not 1 <= prediction_age <= self._config.trading_max_prediction_age_days:
            raise ValueError(
                f"预测日期不满足新鲜度要求: predictionDate={prediction_date}, "
                f"businessDate={business_date}"
            )
        return candidates

    def _existing_buy_target(self, business_date: date) -> str | None:
        prefix = f"LIVE:AFTERNOON_BUY:{business_date.isoformat()}:"
        matching = [
            row
            for row in self._state_store.list_order_intents()
            if str(row["client_order_id"]).startswith(prefix)
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
# AI_GENERATE_END -------------------
