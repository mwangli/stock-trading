# AI_GENERATE_START --------------------
from __future__ import annotations

import logging
import shutil
from collections.abc import Callable
from datetime import date, timedelta
from pathlib import Path
from typing import TypeVar

from stock_trading.broker.client import ZXBrokerClient
from stock_trading.config import AppConfig
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import (
    latest_trade_date,
    live_codes,
    research_codes,
    sync_research_data,
    sync_stock_basics,
)
from stock_trading.research.backtest import (
    backtest_artifact_valid,
    read_backtest_summary,
    run_portfolio_backtest,
)
from stock_trading.research.artifacts import (
    model_artifacts_valid,
    model_root,
    prediction_artifact_path,
    prediction_artifact_valid,
    resolve_current_model_dir,
    train_model_atomically,
)
from stock_trading.research.model import (
    automatic_model_segments,
    predict_alpha158_lightgbm,
)
from stock_trading.research.qlib_store import QlibBinaryStore, to_qlib_code
from stock_trading.state.store import StateStore
from stock_trading.trading.permissions import refresh_permissions

LOGGER = logging.getLogger(__name__)
MINIMUM_RESEARCH_CODES = 100
MINIMUM_COMPLETE_RATIO = 0.80
CAPITAL_SCENARIOS = (2_000, 3_000, 5_000, 10_000)
T = TypeVar("T")


class InitializationService:
    """按固定阶段初始化行情、模型、回测、预测和券商事实。"""

    def __init__(
        self,
        config: AppConfig,
        broker_factory: Callable[[], ZXBrokerClient],
    ) -> None:
        self._config = config
        self._broker_factory = broker_factory
        self._market = ParquetMarketStore(config.data_dir)
        self._state = StateStore(config.state_db_path)

    def run(self, force: bool = False) -> dict[str, object]:
        """执行可恢复初始化；强制模式会先安全删除项目内 var。"""
        if force:
            self._reset_var_directory()
        self._state.initialize()
        LOGGER.info("初始化状态库就绪；各阶段将直接检查实际数据")

        end_date = latest_trade_date(self._config.baostock_proxy)
        start_date = end_date - timedelta(days=self._config.market_history_days)
        minute_cleanup = self._market.cleanup_minutes(end_date)
        LOGGER.info(
            "初始化计划确定：研究日期=%s 至 %s；资金档位=%s",
            start_date,
            end_date,
            ",".join(str(value) for value in CAPITAL_SCENARIOS),
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
            "历史日线、复权和分钟线",
            lambda: self._research_complete(selected_codes, start_date, end_date),
            lambda: sync_research_data(
                self._market,
                self._config.baostock_proxy,
                selected_codes,
                start_date,
                end_date,
                self._config.baostock_workers,
            ),
            lambda: self._research_ready(selected_codes, start_date, end_date),
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
            lambda: qlib.rebuild(model_codes, start_date, end_date),
        )
        segments = automatic_model_segments(
            self._config.qlib_dir / "calendars" / "day.txt"
        )
        model_root_dir = model_root(self._config.artifact_dir)
        model_result = self._step(
            4,
            "基础模型训练",
            lambda: qlib_result is None and _model_valid(model_root_dir),
            lambda: train_model_atomically(
                self._config.qlib_dir, model_root_dir, segments
            ),
        )
        model_dir = resolve_current_model_dir(model_root_dir)

        backtest_result = self._step(
            5,
            "多资金档位回测",
            lambda: model_result is None and self._backtests_valid(),
            lambda: self._run_backtests(model_dir),
        )
        backtests = (
            backtest_result
            if backtest_result is not None
            else self._read_backtest_summaries()
        )

        baseline_dir = self._backtest_dir(3_000)
        self._step(
            6,
            "模型和回测状态导入",
            lambda: backtest_result is None
            and self._state_import_valid(model_dir),
            lambda: self._import_research_state(model_dir, baseline_dir, end_date),
        )

        prediction_result = self._step(
            7,
            "最新交易日预测",
            lambda: self._prediction_valid(model_dir, end_date),
            lambda: self._predict_latest(model_dir, end_date, current_codes),
        )
        if prediction_result is None:
            prediction_result = self._prediction_summary(model_dir, end_date)

        broker_result = self._sync_broker_history(start_date, end_date)
        if broker_result:
            permissions = refresh_permissions(self._state, broker_result.get("orders", ()), end_date)
        else:
            permissions = refresh_permissions(self._state, (), end_date)
        readiness = {
            "modelReady": _model_valid(model_root_dir),
            "predictionReady": self._prediction_valid(model_dir, end_date),
            "brokerReady": broker_result is not None,
        }
        LOGGER.info(
            "初始化完成：模型=%s；预测=%s；券商=%s",
            readiness["modelReady"],
            readiness["predictionReady"],
            readiness["brokerReady"],
        )
        return {
            "startDate": start_date.isoformat(),
            "minuteCleanup": minute_cleanup,
            "endDate": end_date.isoformat(),
            "researchCodes": len(model_codes),
            "collectedCodes": len(valid_codes),
            "liveCodes": len(current_codes),
            "capitalScenarios": list(CAPITAL_SCENARIOS),
            "backtests": backtests,
            "prediction": prediction_result,
            "permissions": permissions,
        } | readiness

    def _step(
        self,
        index: int,
        title: str,
        validator: Callable[[], bool],
        action: Callable[[], T],
        post_validator: Callable[[], bool] | None = None,
    ) -> T | None:
        LOGGER.info("初始化阶段 %d/9：%s", index, title)
        if validator():
            LOGGER.info("初始化阶段跳过：%s；实际数据检查通过", title)
            return None
        result = action()
        if not (post_validator or validator)():
            raise ValueError(f"初始化阶段完成后检查失败: {title}")
        LOGGER.info("初始化阶段完成：%s", title)
        return result

    def _research_complete(
        self, codes: list[str], start_date: date, end_date: date
    ) -> bool:
        return len(self._market.valid_research_codes(codes, start_date, end_date)) == len(codes)

    def _research_ready(
        self, codes: list[str], start_date: date, end_date: date
    ) -> bool:
        valid = self._market.valid_research_codes(codes, start_date, end_date)
        required = max(MINIMUM_RESEARCH_CODES, int(len(codes) * MINIMUM_COMPLETE_RATIO))
        return len(valid) >= required

    def _backtest_dir(self, cash: int) -> Path:
        return self._config.artifact_dir / "backtests" / f"cash-{cash}"

    def _backtests_valid(self) -> bool:
        return all(
            backtest_artifact_valid(self._backtest_dir(cash))
            for cash in CAPITAL_SCENARIOS
        )

    def _run_backtests(self, model_dir: Path) -> dict[str, object]:
        results: dict[str, object] = {}
        for index, cash in enumerate(CAPITAL_SCENARIOS, start=1):
            LOGGER.info("资金档位回测：进度=%d/%d；初始资金=%d", index, len(CAPITAL_SCENARIOS), cash)
            target = self._backtest_dir(cash)
            shutil.rmtree(target, ignore_errors=True)
            results[str(cash)] = run_portfolio_backtest(
                self._config, model_dir, target, cash
            )
        return results

    def _read_backtest_summaries(self) -> dict[str, object]:
        return {
            str(cash): read_backtest_summary(self._backtest_dir(cash))
            for cash in CAPITAL_SCENARIOS
        }

    def _state_import_valid(self, model_dir: Path) -> bool:
        return self._state.research_artifacts_registered(
            model_dir,
            {
                f"capital-{cash}": self._backtest_dir(cash)
                for cash in CAPITAL_SCENARIOS
            },
        )

    def _import_research_state(
        self, model_dir: Path, baseline_dir: Path, business_date: date
    ) -> dict[str, object]:
        result = self._state.import_research_artifacts(
            model_dir, baseline_dir, "capital-3000"
        )
        for cash in CAPITAL_SCENARIOS:
            if cash == 3_000:
                continue
            self._state.import_backtest_performance(
                self._backtest_dir(cash), f"capital-{cash}", business_date.isoformat()
            )
        return result

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
            eligible_instruments={to_qlib_code(code) for code in current_codes},
        )
        self._state.import_prediction_artifact(model_dir, Path(str(result["path"])))
        return result

    def _prediction_summary(self, model_dir: Path, prediction_date: date) -> dict[str, object]:
        path = prediction_artifact_path(model_dir, prediction_date)
        return {
            "predictionDate": prediction_date.isoformat(),
            "topK": 5,
            "path": str(path),
        }

    def _sync_broker_history(
        self, start_date: date, end_date: date
    ) -> dict[str, object] | None:
        LOGGER.info("初始化阶段 8/9：券商历史事实和账户快照")
        try:
            broker = self._broker_factory()
            snapshot = broker.read_history_snapshot(start_date, end_date)
            sync = self._state.sync_broker_snapshot(snapshot, end_date.isoformat())
            return {"sync": sync, "orders": snapshot.orders, "fills": snapshot.fills}
        except Exception as error:
            LOGGER.warning("券商初始化已跳过，不影响模型可用性：%s", error)
            return None

    def _reset_var_directory(self) -> None:
        root = (self._config.project_dir / "var").resolve()
        project = self._config.project_dir.resolve()
        expected = (project / "var").resolve()
        configured = (
            self._config.data_dir.resolve(),
            self._config.qlib_dir.resolve(),
            self._config.artifact_dir.resolve(),
            self._config.state_db_path.resolve(),
        )
        if root != expected or root == project or root.parent != project:
            raise ValueError(f"拒绝删除不安全的初始化目录: {root}")
        if any(root != path and root not in path.parents for path in configured):
            raise ValueError("--force 仅支持全部运行目录位于项目 var 下")
        LOGGER.warning("强制初始化：即将删除全部运行数据；目录=%s", root)
        shutil.rmtree(root, ignore_errors=True)


def _model_valid(model_root_dir: Path) -> bool:
    try:
        return model_artifacts_valid(resolve_current_model_dir(model_root_dir))
    except (OSError, ValueError):
        return False
# AI_GENERATE_END --------------------
