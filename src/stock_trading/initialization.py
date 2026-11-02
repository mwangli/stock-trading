# AI_GENERATE_START ------------------------------------------------------
from __future__ import annotations

import logging
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from time import perf_counter
from typing import Generic, TypeVar

from stock_trading.config import AppConfig
from stock_trading.instruments import normalize_instrument
from stock_trading.market.storage import ParquetMarketStore, ResearchDataAudit
from stock_trading.logging_utils import fixed_log_step, set_log_context
from stock_trading.market.sync import (
    live_codes,
    research_codes,
    sync_collection_data,
    sync_stock_basics,
)
from stock_trading.research.artifacts import (
    model_artifacts_valid,
    model_root,
    prediction_artifact_path,
    prediction_artifact_valid,
    resolve_current_model_dir,
)
from stock_trading.research.model import (
    predict_alpha158_lightgbm,
)
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.research.training import (
    activate_trained_model,
    read_backtest_summaries,
    refresh_qlib_from_parquet,
    train_prepared_model,
    training_artifacts_valid,
)
from stock_trading.state.store import StateStore
from stock_trading.time_utils import shanghai_today
from stock_trading.market.trade_calendar import latest_trade_date

LOGGER = logging.getLogger(__name__)
MINIMUM_RESEARCH_CODES = 100
T = TypeVar("T")


@dataclass(frozen=True)
class StepResult(Generic[T]):
    executed: bool
    value: T | None = None


class InitializationService:
    """按固定阶段初始化行情、Qlib、模型、回测和预测。"""

    def __init__(self, config: AppConfig) -> None:
        self._config = config
        self._market = ParquetMarketStore(config.data_dir)
        self._state = StateStore(config.state_db_path)

    def run(self) -> dict[str, object]:
        """执行可恢复初始化。"""
        set_log_context(step="0/5", progress="-/-", target="状态库")
        self._state.initialize()
        LOGGER.info("状态库就绪，将按实际数据检查各初始化步骤")

        end_date = latest_trade_date(shanghai_today())
        start_date = end_date - timedelta(days=self._config.market_history_days)
        LOGGER.info(
            "初始化计划确定：最近交易日=%s；研究日期=%s 至 %s；资金档位=%s",
            end_date,
            start_date,
            end_date,
            ",".join(str(value) for value in self._config.backtest_capital_scenarios),
        )

        self._step(
            1,
            "股票基础信息",
            lambda: self._market.stock_basics_valid(end_date),
            lambda: sync_stock_basics(
                self._market, self._config.baostock_proxy, end_date
            ),
        )
        stocks = self._market.read_stock_basics()
        selected_codes = research_codes(stocks, start_date, end_date)
        live_metadata_codes = live_codes(stocks, end_date)
        if len(selected_codes) < MINIMUM_RESEARCH_CODES:
            raise ValueError(
                f"满足 SH60、SZ00 研究规则的股票不足: {len(selected_codes)}"
            )
        LOGGER.info(
            "股票池确定：历史研究=%d；当前快照=%d；历史窗口=%s 至 %s",
            len(selected_codes),
            len(live_metadata_codes),
            start_date,
            end_date,
        )

        market_step = self._market_data_step(
            selected_codes, start_date, end_date
        )
        audit = market_step.value
        if audit is None:
            raise ValueError("行情初始化未返回检查结果")
        valid_codes = list(audit.valid_codes)
        model_codes = list(audit.model_codes)
        tradable = set(audit.tradable_codes)
        current_codes = [code for code in live_metadata_codes if code in tradable]
        LOGGER.info(
            "研究数据检查完成：采集有效=%d；模型有效=%d；实时可交易=%d；目标=%d；完整率=%.2f%%",
            len(valid_codes),
            len(model_codes),
            len(current_codes),
            len(selected_codes),
            len(valid_codes) / len(selected_codes) * 100,
        )

        qlib = QlibBinaryStore(self._market, self._config.qlib_dir)
        qlib_step = self._step(
            3,
            "Qlib 数据构建",
            lambda: not market_step.executed
            and qlib.is_valid(model_codes, start_date, end_date),
            lambda: refresh_qlib_from_parquet(
                self._config,
                model_codes,
                start_date,
                end_date,
                audit=audit,
            ),
        )
        model_root_dir = model_root(self._config.artifact_dir)
        training_step = self._step(
            4,
            "模型训练和回测评估",
            lambda: not qlib_step.executed and self._training_valid(model_root_dir),
            lambda: train_prepared_model(self._config, None),
            lambda _result: self._training_valid(model_root_dir),
        )
        model_dir = resolve_current_model_dir(model_root_dir)
        if not training_step.executed:
            activate_trained_model(self._config, model_dir)
        backtests = read_backtest_summaries(self._config, model_dir)

        prediction_step = self._step(
            5,
            "最新交易日完整排名",
            lambda: self._prediction_valid(model_dir, end_date),
            lambda: self._predict_latest(model_dir, end_date, current_codes),
        )
        prediction_result = (
            prediction_step.value
            if prediction_step.executed
            else self._prediction_summary(model_dir, end_date)
        )

        readiness = {
            "modelReady": _model_valid(model_root_dir),
            "predictionReady": self._prediction_valid(model_dir, end_date),
        }
        set_log_context(step="5/5", progress="-/-", target="初始化")
        LOGGER.info(
            "初始化完成：模型=%s；排名=%s",
            readiness["modelReady"],
            readiness["predictionReady"],
        )
        return {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "researchCodes": len(model_codes),
            "collectedCodes": len(valid_codes),
            "liveCodes": len(current_codes),
            "capitalScenarios": list(self._config.backtest_capital_scenarios),
            "backtests": backtests,
            "prediction": prediction_result,
        } | readiness

    def _step(
        self,
        index: int,
        title: str,
        validator: Callable[[], bool],
        action: Callable[[], T],
        post_validator: Callable[[T], bool] | None = None,
    ) -> StepResult[T]:
        step = f"{index}/5"
        set_log_context(step=step, progress="-/-", target=title)
        with fixed_log_step(step):
            started_at = perf_counter()
            LOGGER.info("开始检查初始化步骤")
            if validator():
                LOGGER.info(
                    "本地数据检查通过，初始化步骤跳过：耗时=%.2f 秒",
                    perf_counter() - started_at,
                )
                return StepResult(executed=False)
            LOGGER.info("本地数据检查未通过，开始执行初始化步骤")
            result = action()
            valid = post_validator(result) if post_validator else validator()
            if not valid:
                raise ValueError(f"初始化阶段完成后检查失败: {title}")
            set_log_context(progress="-/-", target=title)
            LOGGER.info(
                "初始化步骤完成：耗时=%.2f 秒",
                perf_counter() - started_at,
            )
            return StepResult(executed=True, value=result)

    def _sync_collection_data(
        self,
        audit: ResearchDataAudit,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> ResearchDataAudit:
        sync_collection_data(
            self._market,
            self._config.baostock_proxy,
            codes,
            start_date,
            end_date,
            skip_existing=True,
            plans=audit.pending,
        )
        refreshed = self._market.audit_research_data(codes, start_date, end_date)
        self._log_missing_collection_plan("下载后缺失清单复查完成", refreshed)
        return refreshed

    def _market_data_step(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> StepResult[ResearchDataAudit]:
        step = "2/5"
        title = "日线、复权因子和5分钟线"
        set_log_context(step=step, progress="0/-", target=title)
        with fixed_log_step(step):
            started_at = perf_counter()
            LOGGER.info("开始检查初始化步骤：股票数=%d", len(codes))
            audit = self._market.audit_research_data(codes, start_date, end_date)
            self._log_missing_collection_plan("原始数据缺失清单生成完成", audit)
            if audit.complete:
                set_log_context(progress="-/-", target=title)
                LOGGER.info(
                    "本地数据检查通过，初始化步骤跳过：耗时=%.2f 秒",
                    perf_counter() - started_at,
                )
                return StepResult(executed=False, value=audit)
            set_log_context(
                progress=f"0/{len(audit.pending)}",
                target="股票数据",
            )
            LOGGER.info(
                "本地数据检查未通过，开始执行初始化步骤：待采集=%d",
                len(audit.pending),
            )
            refreshed = self._sync_collection_data(
                audit, codes, start_date, end_date
            )
            if not refreshed.complete:
                raise ValueError(f"初始化阶段完成后检查失败: {title}")
            set_log_context(progress="-/-", target=title)
            LOGGER.info(
                "初始化步骤完成：耗时=%.2f 秒",
                perf_counter() - started_at,
            )
            return StepResult(executed=True, value=refreshed)

    @staticmethod
    def _log_missing_collection_plan(
        message: str,
        audit: ResearchDataAudit,
    ) -> None:
        set_log_context(progress="-/-", target="缺失清单")
        LOGGER.info(
            "%s：缺失股票=%d；日线=%d；复权因子=%d；5分钟线=%d",
            message,
            len(audit.pending),
            sum(plan.needs_daily for plan in audit.pending),
            sum(plan.needs_adjustment for plan in audit.pending),
            sum(plan.needs_minute5 for plan in audit.pending),
        )

    def _training_valid(self, model_root_dir: Path) -> bool:
        try:
            model_dir = resolve_current_model_dir(model_root_dir)
        except (OSError, ValueError):
            return False
        return training_artifacts_valid(model_dir)

    def _prediction_valid(self, model_dir: Path, prediction_date: date) -> bool:
        return (
            prediction_artifact_valid(model_dir, prediction_date)
            and self._state.prediction_registered(
                model_dir / "model.pkl", prediction_date.isoformat()
            )
        )

    def _predict_latest(
        self, model_dir: Path, prediction_date: date, current_codes: list[str]
    ) -> dict[str, object]:
        result = predict_alpha158_lightgbm(
            self._config.qlib_dir,
            model_dir,
            prediction_date,
            eligible_instruments={normalize_instrument(code) for code in current_codes},
        )
        self._state.import_prediction_artifact(model_dir, Path(str(result["path"])))
        return result

    def _prediction_summary(self, model_dir: Path, prediction_date: date) -> dict[str, object]:
        path = prediction_artifact_path(model_dir, prediction_date)
        return {
            "predictionDate": prediction_date.isoformat(),
            "artifactType": "FULL_CROSS_SECTION_RANKING",
            "path": str(path),
        }

def _model_valid(model_root_dir: Path) -> bool:
    try:
        return model_artifacts_valid(resolve_current_model_dir(model_root_dir))
    except (OSError, ValueError):
        return False
# AI_GENERATE_END ------------------------------------------------------
