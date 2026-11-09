# AI_GENERATE_START--- ----------
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class StrategySpec:
    """数据等级唯一绑定的标签和交易合同。"""

    strategy_id: str
    display_name: str
    label_contract: str
    signal_timing: str
    sell_window: tuple[str, str]
    buy_window: tuple[str, str]
    price_benchmark: str


@dataclass(frozen=True)
class ModelSpec:
    """第一阶段模型矩阵中的一个固定单元格。"""

    model_id: str
    data_level: str
    dataset_id: str
    algorithm: str
    feature_frequency: str
    feature_set: str
    business_description: str
    algorithm_parameters: dict[str, object]
    required_fields: tuple[str, ...]
    strategy: StrategySpec

    def prepared_dir(self, root: Path) -> Path:
        return root / self.dataset_id

    def model_dir(self, root: Path) -> Path:
        return root / self.model_id

    @property
    def label_horizon(self) -> int:
        return 1

    @property
    def label_name(self) -> str:
        return "T+1"

    @property
    def label_description(self) -> str:
        return self.strategy.label_contract


L1_STRATEGY = StrategySpec(
    strategy_id="T1_ALL_SELL_ALL_BUY",
    display_name="全天先卖后买、日均价 T+1",
    label_contract="daily_vwap(D+2) / daily_vwap(D+1) - 1",
    signal_timing="after-close",
    sell_window=("09:30", "15:00"),
    buy_window=("09:30", "15:00"),
    price_benchmark="daily-vwap",
)
L2_STRATEGY = StrategySpec(
    strategy_id="T1_AM_SELL_PM_BUY",
    display_name="收盘排名、次日上午卖出、下午买入",
    label_contract=(
        "sell_vwap(D+2,09:30-11:30) / buy_vwap(D+1,14:00-15:00) - 1"
    ),
    signal_timing="after-close",
    sell_window=("09:30", "11:30"),
    buy_window=("14:00", "15:00"),
    price_benchmark="window-vwap",
)
L3_PLANNED_STRATEGY = StrategySpec(
    strategy_id="T1_PM_SELL_PM_BUY",
    display_name="午间分钟决策、下午先卖后买",
    label_contract=(
        "sell_vwap(D+1,13:00-14:00) / buy_vwap(D,14:00-15:00) - 1"
    ),
    signal_timing="after-noon-rank",
    sell_window=("13:00", "14:00"),
    buy_window=("14:00", "15:00"),
    price_benchmark="minute1-window-vwap",
)

ALGORITHMS: dict[str, dict[str, object]] = {
    "LightGBM": {
        "learning_rate": 0.05,
        "num_leaves": 31,
        "max_depth": 6,
        "n_estimators": 300,
    },
    "XGBoost": {
        "learning_rate": 0.05,
        "max_depth": 6,
        "n_estimators": 300,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
    },
}


def _models_for_level(
    level: str,
    frequency: str,
    feature_set: str,
    required_fields: tuple[str, ...],
    strategy: StrategySpec,
) -> tuple[ModelSpec, ...]:
    return tuple(
        ModelSpec(
            model_id=f"{level}_{frequency}_{algorithm}",
            data_level=level,
            dataset_id=f"{level}_{frequency}",
            algorithm=algorithm,
            feature_frequency=frequency,
            feature_set=feature_set,
            business_description=(
                f"{strategy.display_name}的 {algorithm} 横截面排名模型"
            ),
            algorithm_parameters=dict(parameters),
            required_fields=required_fields,
            strategy=strategy,
        )
        for algorithm, parameters in ALGORITHMS.items()
    )


MODEL_SPECS = (
    *_models_for_level(
        "L1",
        "daily",
        "DailyAlpha158Basic",
        ("daily", "basic", "factor"),
        L1_STRATEGY,
    ),
    *_models_for_level(
        "L2",
        "minute5",
        "DailyAlpha158BasicMinute5Path",
        ("daily", "basic", "factor", "minute5"),
        L2_STRATEGY,
    ),
)
DEFAULT_MODEL_ID = "L1_daily_LightGBM"
_MODEL_BY_ID = {spec.model_id: spec for spec in MODEL_SPECS}


def supported_model_ids() -> tuple[str, ...]:
    return tuple(_MODEL_BY_ID)


def resolve_model_spec(value: str | None) -> ModelSpec:
    """只接受第一阶段四个完整模型 ID。"""
    model_id = value.strip() if value and value.strip() else DEFAULT_MODEL_ID
    try:
        return _MODEL_BY_ID[model_id]
    except KeyError:
        supported = "、".join(supported_model_ids())
        raise ValueError(
            f"模型 ID 不存在：{model_id}；支持的模型：{supported}"
        ) from None


# AI_GENERATE_END--- ----------
