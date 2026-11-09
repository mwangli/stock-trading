# AI_GENERATE_START- --
from __future__ import annotations

from datetime import date
from pathlib import Path
from stock_trading.config import AppConfig
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.model import (
    ModelSegments,
    predict_configured_model,
    train_configured_model,
)
from stock_trading.research.model_config import ModelSpec
from stock_trading.research.qlib_store import QlibBinaryStore


class ConfiguredResearchModel:
    """把模型配置映射到统一的准备、训练和排名实现。"""

    def __init__(self, config: AppConfig, spec: ModelSpec) -> None:
        self.spec = spec
        self.qlib_dir = spec.prepared_dir(config.prepared_dir)
        self.artifact_dir = spec.model_dir(config.model_dir)
        self._calendar_dir = config.market_dir / "raw" / "calendar"
        self._qlib = QlibBinaryStore(
            ParquetMarketStore(config.market_dir), self.qlib_dir, spec
        )

    def prepare(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        force: bool = False,
    ) -> dict[str, object]:
        return self._qlib.prepare(codes, start_date, end_date, force=force)

    def train(
        self,
        destination: Path,
        segments: ModelSegments,
    ) -> dict[str, object]:
        return train_configured_model(
            self.spec,
            self.qlib_dir,
            destination,
            segments,
        )

    def rank(
        self,
        prediction_date: date,
        eligible_instruments: set[str] | None = None,
    ) -> dict[str, object]:
        return predict_configured_model(
            self.spec,
            self.qlib_dir,
            self.artifact_dir,
            prediction_date,
            eligible_instruments,
            calendar_dir=self._calendar_dir,
        )

    def date_range(self) -> tuple[date, date] | None:
        return self._qlib.date_range()

    def available_instruments(self, target_date: date) -> set[str] | None:
        return self._qlib.available_instruments(target_date)


def create_research_model(
    config: AppConfig, spec: ModelSpec
) -> ConfiguredResearchModel:
    return ConfiguredResearchModel(config, spec)


# AI_GENERATE_END- --
