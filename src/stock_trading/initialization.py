# AI_GENERATE_START ------------------------------------------
from __future__ import annotations

import logging
from collections.abc import Callable
from datetime import date, timedelta
from pathlib import Path
from typing import TypeVar

from stock_trading.config import AppConfig
from stock_trading.instruments import normalize_instrument
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.logging_utils import set_log_context
from stock_trading.market.sync import (
    latest_trade_date,
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
    read_backtest_summaries,
    refresh_qlib_from_parquet,
    train_prepared_model,
    training_artifacts_valid,
)
from stock_trading.state.store import StateStore

LOGGER = logging.getLogger(__name__)
MINIMUM_RESEARCH_CODES = 100
T = TypeVar("T")


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

        end_date = latest_trade_date(self._config.baostock_proxy)
        start_date = end_date - timedelta(days=self._config.market_history_days)
        LOGGER.info(
            "初始化计划确定：研究日期=%s 至 %s；资金档位=%s",
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

        self._step(
            2,
            "日线、复权因子和5分钟线",
            lambda: self._collection_complete(selected_codes, start_date, end_date),
            lambda: sync_collection_data(
                self._market,
                self._config.baostock_proxy,
                selected_codes,
                start_date,
                end_date,
                skip_existing=True,
            ),
        )
        valid_codes = self._market.valid_research_codes(
            selected_codes, start_date, end_date
        )
        model_codes = self._market.model_research_codes(valid_codes, start_date, end_date)
        current_codes = self._market.tradable_codes_on(live_metadata_codes, end_date)
        LOGGER.info(
            "研究数据检查完成：采集有效=%d；模型有效=%d；实时可交易=%d；目标=%d；完整率=%.2f%%",
            len(valid_codes),
            len(model_codes),
            len(current_codes),
            len(selected_codes),
            len(valid_codes) / len(selected_codes) * 100,
        )

        qlib = QlibBinaryStore(self._market, self._config.qlib_dir)
        qlib_result = self._step(
            3,
            "Qlib 数据构建",
            lambda: qlib.is_valid(model_codes, start_date, end_date),
            lambda: refresh_qlib_from_parquet(
                self._config, model_codes, start_date, end_date
            ),
        )
        model_root_dir = model_root(self._config.artifact_dir)
        self._step(
            4,
            "模型训练和回测评估",
            lambda: qlib_result is None and self._training_valid(model_root_dir),
            lambda: train_prepared_model(self._config, None),
            lambda: self._training_valid(model_root_dir),
        )
        model_dir = resolve_current_model_dir(model_root_dir)
        backtests = read_backtest_summaries(self._config, model_dir)

        prediction_result = self._step(
            5,
            "最新交易日预测",
            lambda: self._prediction_valid(model_dir, end_date),
            lambda: self._predict_latest(model_dir, end_date, current_codes),
        )
        if prediction_result is None:
            prediction_result = self._prediction_summary(model_dir, end_date)

        readiness = {
            "modelReady": _model_valid(model_root_dir),
            "predictionReady": self._prediction_valid(model_dir, end_date),
        }
        set_log_context(step="5/5", progress="-/-", target="初始化")
        LOGGER.info(
            "初始化完成：模型=%s；预测=%s",
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
        post_validator: Callable[[], bool] | None = None,
    ) -> T | None:
        set_log_context(step=f"{index}/5", progress="-/-", target=title)
        LOGGER.info("初始化步骤开始")
        if validator():
            LOGGER.info("实际数据检查通过，初始化步骤跳过")
            return None
        result = action()
        if not (post_validator or validator)():
            raise ValueError(f"初始化阶段完成后检查失败: {title}")
        set_log_context(step=f"{index}/5", progress="-/-", target=title)
        LOGGER.info("初始化步骤完成")
        return result

    def _collection_complete(
        self, codes: list[str], start_date: date, end_date: date
    ) -> bool:
        """确认全部研究股票的数据可读且覆盖初始化窗口。"""
        return len(
            self._market.valid_research_codes(codes, start_date, end_date)
        ) == len(codes)

    def _training_valid(self, model_root_dir: Path) -> bool:
        try:
            model_dir = resolve_current_model_dir(model_root_dir)
        except (OSError, ValueError):
            return False
        return training_artifacts_valid(
            self._config, model_dir
        ) and self._state.model_artifacts_registered(model_dir)

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
# AI_GENERATE_END ------------------------------------------
