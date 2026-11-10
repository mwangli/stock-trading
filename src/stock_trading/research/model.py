# AI_GENERATE_START --------------------------------------------
from __future__ import annotations

import json
import logging
import pickle
from dataclasses import asdict, dataclass
from datetime import date
from decimal import Decimal
from pathlib import Path

import pandas as pd

from stock_trading.contracts import TRADING_TIME_CONTRACT
from stock_trading.config import MODEL_TRAIN_SPLIT
from stock_trading.market.trade_calendar import shift_trade_date
from stock_trading.research.model_config import ModelSpec
from stock_trading.research.qlib_contract import (
    MIN_QLIB_TRADING_DAYS,
    expected_label_contract,
    strategy_contract_hash,
    trading_time_contract,
)
from stock_trading.utils.logging_utils import set_log_context
from stock_trading.utils.time_utils import utc_now

LOGGER = logging.getLogger(__name__)
EARLY_STOPPING_ROUNDS = 30


@dataclass(frozen=True)
class ModelSegments:
    train_start: date
    train_end: date
    valid_start: date
    valid_end: date
    test_start: date
    test_end: date

    def __post_init__(self) -> None:
        if not (
            self.train_start <= self.train_end
            < self.valid_start <= self.valid_end
            < self.test_start <= self.test_end
        ):
            raise ValueError("模型数据区间必须有序且互不重叠")


def train_configured_model(
    spec: ModelSpec,
    qlib_dir: Path,
    artifact_dir: Path,
    segments: ModelSegments,
    top_k: int = 5,
) -> dict[str, object]:
    """按固定模型配置训练一个回归模型并写入研究制品。"""
    import qlib
    from qlib.constant import REG_CN
    from qlib.data.dataset import DatasetH
    from qlib.data.dataset.handler import DataHandlerLP

    set_log_context(step="1/4", progress="-/-", target=spec.model_id)
    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
    handler = _configured_handler(spec)(
        instruments="all",
        start_time=segments.train_start,
        end_time=segments.test_end,
        fit_start_time=segments.train_start,
        fit_end_time=segments.train_end,
        label=(["$label"], ["LABEL0"]),
    )
    dataset = DatasetH(
        handler=handler,
        segments={
            "train": (segments.train_start, segments.train_end),
            "valid": (segments.valid_start, segments.valid_end),
            "test": (segments.test_start, segments.test_end),
        },
    )
    set_log_context(step="2/4", progress="-/-", target="训练数据")
    train = dataset.prepare("train", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L)
    valid = dataset.prepare("valid", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L)
    test_features = dataset.prepare("test", col_set="feature", data_key=DataHandlerLP.DK_I)
    if train.empty or valid.empty or test_features.empty:
        raise ValueError(f"模型数据集为空：{spec.model_id}")
    train_target = train["label"].iloc[:, 0].astype(float)
    valid_target = valid["label"].iloc[:, 0].astype(float)
    model = _configured_estimator(spec)
    set_log_context(step="3/4", progress="0/300", target=spec.algorithm)
    if spec.algorithm == "LightGBM":
        import lightgbm as lgb

        model.fit(
            train["feature"],
            train_target,
            eval_set=[(valid["feature"], valid_target)],
            eval_names=["valid"],
            eval_metric="l2",
            callbacks=[
                lgb.early_stopping(EARLY_STOPPING_ROUNDS, verbose=False),
                _lightgbm_progress_callback(),
            ],
        )
    else:
        model.fit(
            train["feature"],
            train_target,
            eval_set=[(valid["feature"], valid_target)],
            verbose=False,
        )
    predictions = pd.Series(
        _model_predict(model, test_features), index=test_features.index, name="score"
    )
    labels = dataset.prepare("test", col_set="label", data_key=DataHandlerLP.DK_R).iloc[:, 0].rename("label")
    scored = pd.concat([predictions, labels], axis=1)
    scored.index.names = ["datetime", "instrument"]
    set_log_context(step="4/4", progress="-/-", target=str(artifact_dir))
    return _persist_training_artifacts(
        spec,
        model,
        scored,
        artifact_dir,
        segments,
        top_k,
        len(train),
        len(valid),
        train["feature"].shape[1],
    )


def _configured_handler(spec: ModelSpec):
    from stock_trading.research.features import (
        DailyAlpha158,
        DailyMinute5PathAlpha158,
    )

    if spec.data_level == "L1":
        return DailyAlpha158
    if spec.data_level == "L2":
        return DailyMinute5PathAlpha158
    raise ValueError(f"当前阶段不支持的数据等级：{spec.data_level}")


def _configured_estimator(spec: ModelSpec):
    parameters = dict(spec.algorithm_parameters)
    if spec.algorithm == "LightGBM":
        import lightgbm as lgb

        return lgb.LGBMRegressor(
            objective="regression",
            metric="l2",
            n_jobs=2,
            random_state=20261030,
            verbosity=-1,
            **parameters,
        )
    if spec.algorithm == "XGBoost":
        import xgboost as xgb

        return xgb.XGBRegressor(
            objective="reg:squarederror",
            eval_metric="rmse",
            n_jobs=2,
            random_state=20261030,
            early_stopping_rounds=EARLY_STOPPING_ROUNDS,
            **parameters,
        )
    raise ValueError(f"不支持的模型算法：{spec.algorithm}")


def _persist_training_artifacts(
    spec: ModelSpec,
    model,
    scored: pd.DataFrame,
    artifact_dir: Path,
    segments: ModelSegments,
    top_k: int,
    train_rows: int,
    valid_rows: int,
    feature_count: int,
) -> dict[str, object]:
    evaluation = scored.dropna(subset=["score", "label"])
    if evaluation.empty:
        raise ValueError(f"测试集没有有效评分和标签：{spec.model_id}")
    metrics = _cross_sectional_metrics(evaluation)
    top = (
        scored.dropna(subset=["score"])
        .reset_index()
        .sort_values(["datetime", "score", "instrument"], ascending=[True, False, True])
        .groupby("datetime", sort=True)
        .head(top_k)
    )
    artifact_dir.mkdir(parents=True, exist_ok=True)
    scored.reset_index().to_parquet(artifact_dir / "test_predictions.parquet", index=False)
    top.to_parquet(artifact_dir / "top5.parquet", index=False)
    with (artifact_dir / "model.pkl").open("wb") as stream:
        pickle.dump(model, stream)
    payload = {
        "modelId": spec.model_id,
        "featureLevel": spec.data_level,
        "algorithm": spec.algorithm,
        "strategyId": spec.strategy.strategy_id,
        "strategyName": spec.strategy.display_name,
        "strategyContractHash": strategy_contract_hash(spec),
        "dataFeature": spec.feature_frequency,
        "featureSet": spec.feature_set,
        "labelHorizon": 1,
        "labelContract": expected_label_contract(spec),
        "timeContractVersion": TRADING_TIME_CONTRACT,
        "timeContract": trading_time_contract(spec),
        "businessDescription": spec.business_description,
        "model": f"{spec.algorithm} Regression",
        "label": spec.label_description,
        "labelPurgedTradeDays": 2,
        "algorithmParameters": spec.algorithm_parameters,
        "requiredFields": spec.required_fields,
        "segments": _segments_record(segments),
        "metrics": metrics,
        "topK": top_k,
        "rows": len(scored),
        "labeledRows": len(evaluation),
        "predictionDays": int(top["datetime"].nunique()),
        "trainingMode": "single-pass",
        "trainRows": train_rows,
        "validRows": valid_rows,
        "featureCount": feature_count,
    }
    (artifact_dir / "metrics.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return payload


def predict_configured_model(
    spec: ModelSpec,
    qlib_dir: Path,
    model_dir: Path,
    prediction_date: date,
    eligible_instruments: set[str] | None = None,
    calendar_dir: Path | None = None,
) -> dict[str, object]:
    metrics, calendar = _prediction_context(spec, qlib_dir, model_dir, prediction_date)
    features = _prediction_features(
        _configured_handler(spec), qlib_dir, prediction_date, metrics["segments"], calendar
    )
    if features.empty:
        raise ValueError(f"目标日期没有可用模型特征：{prediction_date}")
    scored = _prediction_scores(model_dir, features, eligible_instruments)
    if scored.empty:
        raise ValueError(f"目标日期没有可排名股票：{prediction_date}")
    return {"modelId": spec.model_id, **_persist_prediction(scored, metrics, model_dir, prediction_date, calendar_dir)}


def _prediction_features(handler_type, qlib_dir: Path, prediction_date: date, segments: dict[str, str], calendar: pd.DatetimeIndex) -> pd.DataFrame:
    import qlib
    from qlib.constant import REG_CN
    from qlib.data.dataset import DatasetH
    from qlib.data.dataset.handler import DataHandlerLP

    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
    handler = handler_type(
        instruments="all",
        start_time=pd.Timestamp(calendar.min()).date(),
        end_time=prediction_date,
        fit_start_time=date.fromisoformat(segments["train_start"]),
        fit_end_time=date.fromisoformat(segments["train_end"]),
        label=(["$label"], ["LABEL0"]),
    )
    dataset = DatasetH(handler=handler, segments={"prediction": (prediction_date, prediction_date)})
    return dataset.prepare("prediction", col_set="feature", data_key=DataHandlerLP.DK_I)


def _prediction_context(spec: ModelSpec, qlib_dir: Path, model_dir: Path, prediction_date: date) -> tuple[dict[str, object], pd.DatetimeIndex]:
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    if metrics.get("modelId") != spec.model_id or metrics.get("strategyContractHash") != strategy_contract_hash(spec):
        raise ValueError("模型策略合同不一致；请重新准备数据并训练模型")
    calendar = pd.DatetimeIndex(pd.to_datetime((qlib_dir / "calendars" / "day.txt").read_text(encoding="utf-8").splitlines()))
    if pd.Timestamp(prediction_date) not in calendar:
        raise ValueError(f"Qlib 日历不包含预测日期：{prediction_date}")
    return metrics, calendar


def _prediction_scores(model_dir: Path, features: pd.DataFrame, eligible_instruments: set[str] | None) -> pd.Series:
    with (model_dir / "model.pkl").open("rb") as stream:
        model = pickle.load(stream)
    columns = _model_feature_names(model)
    if columns:
        missing = sorted(set(columns) - set(features.columns))
        if missing:
            raise ValueError(f"预测特征缺少训练字段：{missing[:5]}")
        features = features.loc[:, columns]
    scored = pd.Series(_model_predict(model, features), index=features.index, name="score").dropna()
    scored.index.names = ["datetime", "instrument"]
    if eligible_instruments is not None:
        allowed = {value.upper() for value in eligible_instruments}
        instruments = scored.index.get_level_values("instrument").astype(str).str.upper()
        scored = scored[instruments.isin(allowed)]
    return scored


def _persist_prediction(scored: pd.Series, metrics: dict[str, object], model_dir: Path, prediction_date: date, calendar_dir: Path | None) -> dict[str, object]:
    ranking = scored.reset_index().sort_values(["datetime", "score", "instrument"], ascending=[True, False, True]).reset_index(drop=True)
    ranking["rank"] = range(1, len(ranking) + 1)
    ranking["label"] = float("nan")
    ranking["execution_date"] = shift_trade_date(prediction_date, 1, calendar_dir).isoformat()
    ranking["generated_at"] = utc_now().isoformat()
    ranking["contract_version"] = TRADING_TIME_CONTRACT
    output = model_dir / "predictions" / f"ranking-{prediction_date.isoformat()}.parquet"
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(".parquet.tmp")
    ranking.to_parquet(temporary, index=False)
    temporary.replace(output)
    return {
        "model": metrics["model"],
        "featureSet": metrics["featureSet"],
        "predictionDate": prediction_date.isoformat(),
        "universeRows": len(scored),
        "rankingRows": len(ranking),
        "path": str(output),
        "topCandidates": [
            {"instrument": str(row.instrument), "rank": int(row.rank), "score": float(row.score)}
            for row in ranking.head(5).itertuples(index=False)
        ],
    }


def _model_feature_names(model) -> list[str]:
    names = getattr(model, "feature_names_in_", None)
    if names is not None:
        return list(names)
    names = getattr(model, "feature_name_", None)
    return list(names) if names is not None else []


def _model_predict(model, features: pd.DataFrame):
    if model.__class__.__module__.startswith("xgboost"):
        return model.predict(features)
    best_iteration = getattr(model, "best_iteration_", None)
    return model.predict(features, num_iteration=best_iteration) if best_iteration else model.predict(features)


def _cross_sectional_metrics(frame: pd.DataFrame) -> dict[str, float | int]:
    rows = [_daily_correlations(group) for _, group in frame.groupby(level="datetime", sort=True)]
    daily = pd.DataFrame(rows)
    return {
        "dailySamples": len(daily),
        "validIcDays": int(daily["ic"].notna().sum()),
        "validRankIcDays": int(daily["rankIc"].notna().sum()),
        "constantScoreDays": int(daily["constantScore"].sum()),
        "icMean": float(daily["ic"].mean()),
        "icStd": float(daily["ic"].std()),
        "rankIcMean": float(daily["rankIc"].mean()),
        "rankIcStd": float(daily["rankIc"].std()),
    }


def _daily_correlations(group: pd.DataFrame) -> dict[str, object]:
    constant_score = group["score"].nunique() < 2
    constant_label = group["label"].nunique() < 2
    return {
        "ic": float("nan") if constant_score or constant_label else group["score"].corr(group["label"], method="pearson"),
        "rankIc": float("nan") if constant_score or constant_label else group["score"].corr(group["label"], method="spearman"),
        "constantScore": constant_score,
    }


def _segments_record(segments: ModelSegments) -> dict[str, str]:
    return {key: value.isoformat() for key, value in asdict(segments).items()}


def _lightgbm_progress_callback():
    def callback(environment) -> None:
        current = environment.iteration + 1
        total = environment.end_iteration
        if current == 1 or current == total or current % 25 == 0:
            set_log_context(progress=f"{current}/{total}", target="LightGBM")
            LOGGER.info("训练迭代完成")

    callback.order = 20
    callback.before_iteration = False
    return callback


def automatic_model_segments(
    calendar_path: Path,
    maximum_days: int | None = None,
    label_horizon: int = 1,
) -> ModelSegments:
    calendar = [date.fromisoformat(value) for value in calendar_path.read_text(encoding="utf-8").splitlines() if value.strip()]
    if maximum_days is not None:
        if maximum_days <= 0:
            raise ValueError("训练窗口交易日数必须大于 0")
        calendar = calendar[-maximum_days:]
    if len(calendar) < MIN_QLIB_TRADING_DAYS:
        raise ValueError(f"自动切分至少需要 {MIN_QLIB_TRADING_DAYS} 个交易日，当前={len(calendar)}")
    ratios = tuple(Decimal(str(value)) for value in MODEL_TRAIN_SPLIT)
    if any(not ratio.is_finite() or ratio <= 0 for ratio in ratios):
        raise ValueError("训练、验证和测试比例必须全部大于 0")
    total = sum(ratios, Decimal("0"))
    train_end_index = int(Decimal(len(calendar)) * ratios[0] / total) - 1
    valid_end_index = int(Decimal(len(calendar)) * (ratios[0] + ratios[1]) / total) - 1
    purge = label_horizon + 1
    if min(train_end_index + 1, valid_end_index - train_end_index, len(calendar) - valid_end_index - 1) <= purge:
        raise ValueError("训练、验证或测试区间不足以完成 T+1 标签隔离")
    return ModelSegments(
        train_start=calendar[0],
        train_end=calendar[train_end_index - purge],
        valid_start=calendar[train_end_index + 1],
        valid_end=calendar[valid_end_index - purge],
        test_start=calendar[valid_end_index + 1],
        test_end=calendar[-1 - purge],
    )


# AI_GENERATE_END --------------------------------------------
