# AI_GENERATE_START-2------- ----------
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from stock_trading.config import TRADING_TIME_CONTRACT


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
    holding_policy: str


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
        return 3 if self.data_level == "L2" and "dynamic-holding-value" in self.strategy.label_contract else 1

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
    holding_policy="RANK_EXIT_OR_MAX_HOLD",
)
L2_STRATEGY = StrategySpec(
    strategy_id="T1_AM_SELL_PM_BUY",
    display_name="分钟特征、全天日均价 T+1",
    label_contract="daily_vwap(D+2) / daily_vwap(D+1) - 1",
    signal_timing="after-close",
    sell_window=("09:30", "15:00"),
    buy_window=("09:30", "15:00"),
    price_benchmark="daily-vwap",
    holding_policy="RANK_EXIT_OR_MAX_HOLD",
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
    holding_policy="FIXED_NEXT_TRADING_DAY",
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


def all_model_specs() -> tuple[ModelSpec, ...]:
    """返回全部已配置模型，供批量训练使用。"""
    return MODEL_SPECS


def dataset_specs() -> tuple[ModelSpec, ...]:
    """返回按数据集去重后的模型规格，供统一准备 Qlib 数据使用。"""
    result: list[ModelSpec] = []
    seen: set[str] = set()
    for spec in MODEL_SPECS:
        if spec.dataset_id not in seen:
            result.append(spec)
            seen.add(spec.dataset_id)
    return tuple(result)


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


QLIB_CONTRACT_VERSION = "19"
MIN_QLIB_TRADING_DAYS = 63


def strategy_contract(spec: ModelSpec) -> dict[str, object]:
    strategy = spec.strategy
    return {
        "version": TRADING_TIME_CONTRACT,
        "strategyId": strategy.strategy_id,
        "featureLevel": spec.data_level,
        "featureFrequency": spec.feature_frequency,
        "signalTiming": strategy.signal_timing,
        "sellWindow": list(strategy.sell_window),
        "buyWindow": list(strategy.buy_window),
        "priceBenchmark": strategy.price_benchmark,
        "holdingPolicy": strategy.holding_policy,
        "labelContract": strategy.label_contract,
        "exitPosition": 5,
        "exitFallbackPosition": 20,
    }


def strategy_contract_hash(spec: ModelSpec) -> str:
    payload = json.dumps(
        strategy_contract(spec),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def expected_label_contract(spec: ModelSpec) -> str:
    return f"{spec.strategy.strategy_id}:{spec.strategy.label_contract}"


def trading_time_contract(spec: ModelSpec) -> dict[str, object]:
    return strategy_contract(spec)


def expected_qlib_contract(spec: ModelSpec) -> str:
    return f"{QLIB_CONTRACT_VERSION}:{spec.dataset_id}:{strategy_contract_hash(spec)}"


# AI_GENERATE_END-2------- ----------
