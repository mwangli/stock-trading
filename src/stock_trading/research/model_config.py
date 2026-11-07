# AI_GENERATE_START -------
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable


@dataclass(frozen=True)
class ModelSpec:
    """一套可独立准备、训练、排名和复盘的研究模型配置。"""

    model_id: str
    version: tuple[int, ...]
    algorithm: str
    feature_frequency: str
    feature_set: str
    label_horizon: int
    business_description: str
    algorithm_parameters: dict[str, object]
    required_fields: tuple[str, ...]
    training_target: str = "raw_return"
    training_window_days: int | None = None

    def qlib_dir(self, root: Path) -> Path:
        return root / self.model_id

    def artifact_dir(self, root: Path) -> Path:
        return root / self.model_id

    @property
    def label_name(self) -> str:
        return f"T+{self.label_horizon}"

    @property
    def label_description(self) -> str:
        return (
            "预测日 D 收盘后生成排名，D+1 14:00–15:00 窗口 VWAP 买入，"
            f"持有 {self.label_horizon} 个交易日后 13:00–14:00 窗口 VWAP 卖出的收益率"
        )


MODEL_SPECS = (
    ModelSpec(
        model_id="v1.1_LightGBM_daily_T+1",
        version=(1, 1),
        algorithm="LightGBM",
        feature_frequency="daily",
        feature_set="DailyAlpha158",
        label_horizon=1,
        business_description="使用排名日 D 收盘后完整日线的 LightGBM T+1 排名模型。",
        algorithm_parameters={
            "learning_rate": 0.05,
            "num_leaves": 31,
            "max_depth": 6,
            "n_estimators": 300,
        },
        required_fields=("daily", "factor", "minute_summary"),
    ),
    ModelSpec(
        model_id="v1.2_LightGBM_minute5_T+1",
        version=(1, 2),
        algorithm="LightGBM",
        feature_frequency="minute5",
        feature_set="MorningAlpha158",
        label_horizon=1,
        business_description="使用预测日上午 5 分钟行情、服务次日买入的 LightGBM T+1 模型。",
        algorithm_parameters={
            "learning_rate": 0.05,
            "num_leaves": 31,
            "max_depth": 6,
            "n_estimators": 300,
        },
        required_fields=("minute5", "factor", "minute_summary"),
    ),
    ModelSpec(
        model_id="v1.3_LightGBM_minute1_T+1",
        version=(1, 3),
        algorithm="LightGBM",
        feature_frequency="minute1",
        feature_set="FullDayMinute1Alpha158",
        label_horizon=1,
        business_description=(
            "使用排名日 D 完整1分钟量价与日线背景的 LightGBM 稳健 T+1 收益模型。"
        ),
        algorithm_parameters={
            "objective": "huber",
            "learning_rate": 0.03,
            "num_leaves": 31,
            "max_depth": 6,
            "n_estimators": 500,
        },
        required_fields=("minute1", "daily", "factor", "minute_summary"),
        training_target="winsorized_return",
        training_window_days=500,
    ),
    ModelSpec(
        model_id="v2.1_XGBoost_minute5_T+1",
        version=(2, 1),
        algorithm="XGBoost",
        feature_frequency="minute5",
        feature_set="MorningAlpha158",
        label_horizon=1,
        business_description="使用预测日上午 5 分钟行情、服务次日买入的 XGBoost T+1 模型。",
        algorithm_parameters={
            "learning_rate": 0.05,
            "max_depth": 6,
            "n_estimators": 300,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
        },
        required_fields=("minute5", "factor", "minute_summary"),
    ),
    ModelSpec(
        model_id="v2.3_XGBoostRanker_minute1_T+1",
        version=(2, 3),
        algorithm="XGBoostRanker",
        feature_frequency="minute1",
        feature_set="FullDayMinute1Alpha158",
        label_horizon=1,
        business_description=(
            "使用排名日 D 完整1分钟量价、按交易日分组优化横截面顺序的 T+1 模型。"
        ),
        algorithm_parameters={
            "learning_rate": 0.03,
            "max_depth": 6,
            "n_estimators": 500,
            "subsample": 0.8,
            "colsample_bytree": 0.8,
        },
        required_fields=("minute1", "daily", "factor", "minute_summary"),
        training_target="cross_sectional_rank",
        training_window_days=500,
    ),
)
DEFAULT_MODEL_ID = "v2.1_XGBoost_minute5_T+1"


def supported_model_ids() -> tuple[str, ...]:
    return tuple(spec.model_id for spec in MODEL_SPECS)


def resolve_model_spec(
    value: str | None,
    warning: Callable[[str], None] | None = None,
) -> ModelSpec:
    """按完整 ID、版本前缀或算法前缀选择数字版本最高的模型。"""
    if value is None or not value.strip():
        return next(spec for spec in MODEL_SPECS if spec.model_id == DEFAULT_MODEL_ID)
    query = value.strip().lower()
    exact = [spec for spec in MODEL_SPECS if spec.model_id.lower() == query]
    matches = exact or [
        spec
        for spec in MODEL_SPECS
        if spec.model_id.lower().startswith(query)
        or spec.algorithm.lower().startswith(query)
    ]
    if not matches:
        supported = "、".join(supported_model_ids())
        raise ValueError(f"模型 ID 不存在：{value}；支持的模型：{supported}")
    selected = max(matches, key=lambda item: item.version)
    if len(matches) > 1 and warning is not None:
        warning(
            f"模型参数 {value} 匹配多个版本，已选择最高版本 {selected.model_id}"
        )
    return selected


# AI_GENERATE_END -------
