# AI_GENERATE_START -------
from __future__ import annotations

import json
from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.models import BrokerSyncSnapshot, FiveLevelQuote
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore


class BrokerValidationClient(Protocol):
    def read_shadow_snapshot(self) -> BrokerSyncSnapshot: ...

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote: ...


class RealValidationService:
    """Run local gates first, then validate the real broker through read-only facts only."""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerValidationClient,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def validate(self, business_date: date) -> dict[str, object]:
        model_version_id = self._state_store.active_model_id()
        candidates = self._state_store.latest_prediction_candidates(
            business_date.isoformat(), 5, model_version_id=model_version_id
        )
        blockers = self._local_blockers(business_date, model_version_id, candidates)
        if blockers:
            validation = self._state_store.save_real_validation(
                business_date.isoformat(),
                model_version_id,
                "BLOCKED",
                blockers,
                {"brokerHttpAttempted": False},
            )
            return self._result(validation)

        try:
            snapshot = self._broker.read_shadow_snapshot()
            sync = self._state_store.sync_broker_snapshot(
                snapshot, business_date.isoformat()
            )
            blockers.extend(self._snapshot_blockers(snapshot))
            selected_candidate = self._validate_candidate_quotes(candidates, blockers)
            summary = {
                "brokerHttpAttempted": True,
                "availableCash": float(snapshot.account.available_cash),
                "totalAssets": float(snapshot.account.total_assets),
                "positionCount": len(snapshot.positions),
                "orderCount": len(snapshot.orders),
                "fillCount": len(snapshot.fills),
                "selectedCandidate": selected_candidate,
                "brokerSyncStatus": sync["status"],
            }
        except Exception as error:
            blockers.append(f"券商只读调用失败: {type(error).__name__}: {error}")
            summary = {
                "brokerHttpAttempted": True,
                "errorType": type(error).__name__,
            }
        status = "READ_ONLY_VERIFIED" if not blockers else "BLOCKED"
        validation = self._state_store.save_real_validation(
            business_date.isoformat(),
            model_version_id,
            status,
            blockers,
            summary,
        )
        return self._result(validation)

    def _local_blockers(
        self,
        business_date: date,
        model_version_id: int | None,
        candidates: list[dict[str, object]],
    ) -> list[str]:
        blockers: list[str] = []
        if business_date != date.today():
            blockers.append("真实券商只读验证必须使用当前日期")
        calendar_path = self._config.qlib_dir / "calendars" / "day.txt"
        if not calendar_path.exists():
            blockers.append("Qlib 交易日历不存在")
        elif business_date.isoformat() not in {
            value.strip() for value in calendar_path.read_text(encoding="utf-8").splitlines()
        }:
            blockers.append("目标日期不是 Qlib 交易日")
        if not self._config.broker_enabled:
            blockers.append("ZX 券商接入未启用")
        required_values = {
            "ZXBROKER_MOBILE_CODE": self._config.broker_mobile_code,
            "ZXBROKER_ACCOUNT": self._config.broker_account,
            "ZXBROKER_ENCODED_PASSWORD": self._config.broker_encoded_password,
            "ZXBROKER_SIGN_KEY": self._config.broker_sign_key,
            "ZXBROKER_INTACT_TO_SERVER": self._config.broker_intact_to_server,
        }
        missing = [name for name, value in required_values.items() if not value]
        if missing:
            blockers.append(f"缺少券商配置: {','.join(missing)}")
        automatic_login_ready = (
            self._config.broker_captcha_auto_submit_enabled
            and bool(self._config.broker_baidu_ocr_api_key)
            and bool(self._config.broker_baidu_ocr_secret_key)
        )
        if not self._config.broker_token and not automatic_login_ready:
            blockers.append("缺少可用 Token，且百度 OCR 自动登录未完整配置")
        if model_version_id is None:
            blockers.append("没有 ACTIVE 模型")
        if not candidates:
            blockers.append("目标交易日前没有 Top 5 预测")
        elif model_version_id is not None and any(
            int(candidate["model_version_id"]) != model_version_id
            for candidate in candidates
        ):
            blockers.append("最新 Top 5 不属于当前 ACTIVE 模型")
        elif not 1 <= (
            business_date - date.fromisoformat(str(candidates[0]["prediction_date"]))
        ).days <= self._config.trading_max_prediction_age_days:
            blockers.append("Top 5 预测日期不满足新鲜度要求")
        active_intents = self._state_store.list_order_intents(active_only=True)
        if active_intents:
            blockers.append("存在未确认终态订单")
        return blockers

    def _snapshot_blockers(self, snapshot: BrokerSyncSnapshot) -> list[str]:
        blockers: list[str] = []
        if snapshot.account.total_assets <= 0:
            blockers.append("券商总资产必须大于零")
        if snapshot.account.total_assets > Decimal(
            str(self._config.real_validation_max_assets)
        ):
            blockers.append("券商总资产超过小资金验证上限")
        if snapshot.account.available_cash < 0:
            blockers.append("券商可用资金不能为负数")
        if any(
            position.quantity < 0
            or position.available_quantity < 0
            or position.available_quantity > position.quantity
            for position in snapshot.positions
        ):
            blockers.append("券商持仓数量或可卖数量非法")
        if any(order.status == "UNKNOWN" for order in snapshot.orders):
            blockers.append("券商当日委托包含未知状态")
        return blockers

    def _validate_candidate_quotes(
        self,
        candidates: list[dict[str, object]],
        blockers: list[str],
    ) -> str | None:
        minimum = Decimal(str(self._config.trading_min_price))
        maximum = Decimal(str(self._config.trading_max_price))
        quote_errors: list[str] = []
        for candidate in candidates:
            instrument = str(candidate["instrument"]).upper()
            if self._config.trading_allowed_prefixes and not instrument.startswith(
                self._config.trading_allowed_prefixes
            ):
                continue
            try:
                quote = self._broker.get_five_level_quote(instrument[-6:])
            except Exception as error:
                quote_errors.append(f"{instrument}:{type(error).__name__}")
                continue
            reference_price = quote.price or quote.asks[0]
            if minimum <= reference_price <= maximum:
                return instrument
        detail = f"，行情失败: {','.join(quote_errors)}" if quote_errors else ""
        blockers.append(f"Top 5 中没有权限和价格均可交易的候选{detail}")
        return None

    @staticmethod
    def _result(validation: dict[str, object]) -> dict[str, object]:
        return {
            "businessDate": validation["business_date"],
            "modelVersionId": validation["model_version_id"],
            "status": validation["status"],
            "blockers": json.loads(str(validation["blockers_json"])),
            "summary": json.loads(str(validation["summary_json"])),
        }
# AI_GENERATE_END -------
