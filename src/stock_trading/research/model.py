# AI_GENERATE_START --------------------
from __future__ import annotations

import json
import logging
import pickle
import shutil
from dataclasses import asdict, dataclass
from datetime import date
from decimal import Decimal
from pathlib import Path

import pandas as pd

from stock_trading.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)


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
            raise ValueError("model segments must be ordered and must not overlap")


def train_alpha158_lightgbm_adaptive(
    qlib_dir: Path,
    artifact_dir: Path,
    segments: ModelSegments,
    top_k: int = 5,
    feature_batch_days: int = 30,
) -> dict[str, object]:
    """Select the existing single-pass path or the low-memory batched path."""
    from stock_trading.research.batched_model import (
        GIB,
        MEMORY_THRESHOLD_BYTES,
        total_physical_memory_bytes,
        train_alpha158_lightgbm_batched,
        use_batched_training,
    )

    total_memory = total_physical_memory_bytes()
    batched = use_batched_training(total_memory)
    memory_text = "unknown" if total_memory is None else f"{total_memory / GIB:.1f} GiB"
    LOGGER.info(
        "训练内存策略：总内存=%s；阈值=%.1f GiB；模式=%s",
        memory_text,
        MEMORY_THRESHOLD_BYTES / GIB,
        "batched" if batched else "single-pass",
    )
    if not batched:
        return train_alpha158_lightgbm(qlib_dir, artifact_dir, segments, top_k)

    work_dir = artifact_dir / ".feature-batches"
    try:
        result = train_alpha158_lightgbm_batched(
            qlib_dir,
            work_dir,
            segments,
            _lightgbm_progress_callback(),
            feature_batch_days,
        )
        return _persist_training_artifacts(
            result.model,
            result.scored,
            artifact_dir,
            segments,
            top_k,
            training_mode="batched",
            train_rows=result.train_rows,
            valid_rows=result.valid_rows,
            feature_count=result.feature_count,
            feature_batch_days=result.feature_batch_days,
        )
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


def train_alpha158_lightgbm(
    qlib_dir: Path,
    artifact_dir: Path,
    segments: ModelSegments,
    top_k: int = 5,
) -> dict[str, object]:
    """Train the fixed Alpha158 LightGBM baseline and persist predictions and metrics."""
    import qlib
    import lightgbm as lgb
    from qlib.constant import REG_CN
    from qlib.contrib.data.handler import Alpha158
    from qlib.data.dataset import DatasetH
    from qlib.data.dataset.handler import DataHandlerLP

    set_log_context(progress="1/6", target="Qlib")
    LOGGER.info("初始化 Qlib：目录=%s", qlib_dir)
    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
    set_log_context(progress="2/6", target="Alpha158")
    LOGGER.info("构建特征处理器")
    handler = Alpha158(
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
    set_log_context(progress="3/6", target="训练数据")
    LOGGER.info("准备训练、验证和测试数据")
    train = dataset.prepare(
        "train", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    )
    valid = dataset.prepare(
        "valid", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    )
    test_features = dataset.prepare(
        "test", col_set="feature", data_key=DataHandlerLP.DK_I
    )
    LOGGER.info(
        "模型数据准备完成：训练样本=%d；验证样本=%d；测试样本=%d；特征数=%d",
        len(train),
        len(valid),
        len(test_features),
        train["feature"].shape[1],
    )
    model = lgb.LGBMRegressor(
        objective="regression",
        n_estimators=300,
        learning_rate=0.05,
        num_leaves=31,
        max_depth=6,
        subsample=0.8,
        colsample_bytree=0.8,
        lambda_l1=1.0,
        lambda_l2=1.0,
        n_jobs=2,
        random_state=20261030,
        verbosity=-1,
    )
    set_log_context(progress="4/6", target="LightGBM")
    LOGGER.info("开始模型迭代：最大迭代=300；早停轮数=30")
    model.fit(
        train["feature"],
        train["label"].iloc[:, 0],
        eval_set=[(valid["feature"], valid["label"].iloc[:, 0])],
        eval_names=["valid"],
        eval_metric="l2",
        callbacks=[
            lgb.early_stopping(30, verbose=False),
            _lightgbm_progress_callback(),
        ],
    )
    LOGGER.info("LightGBM 训练完成：最佳迭代=%s", getattr(model, "best_iteration_", None))
    set_log_context(progress="5/6", target="测试集评分")
    LOGGER.info("生成测试集评分和 Top %d", top_k)
    predictions = pd.Series(
        model.predict(test_features), index=test_features.index, name="score"
    )
    labels = dataset.prepare(
        "test", col_set="label", data_key=DataHandlerLP.DK_R
    ).iloc[:, 0].rename("label")
    scored = pd.concat([predictions, labels], axis=1)
    scored.index.names = ["datetime", "instrument"]
    evaluation = scored.dropna(subset=["score", "label"])
    metrics = _cross_sectional_metrics(evaluation)
    top = (
        scored.dropna(subset=["score"])
        .reset_index()
        .sort_values(
            ["datetime", "score", "instrument"], ascending=[True, False, True]
        )
        .groupby("datetime", sort=True)
        .head(top_k)
    )

    LOGGER.info(
        "测试集评估完成：有效样本=%d；IC均值=%s；Rank IC均值=%s",
        len(evaluation),
        metrics["icMean"],
        metrics["rankIcMean"],
    )
    set_log_context(progress="6/6", target=str(artifact_dir))
    LOGGER.info("写入模型、预测和指标制品")
    artifact_dir.mkdir(parents=True, exist_ok=True)
    scored.reset_index().to_parquet(artifact_dir / "test_predictions.parquet", index=False)
    top.to_parquet(artifact_dir / "top5.parquet", index=False)
    with (artifact_dir / "model.pkl").open("wb") as stream:
        pickle.dump(model, stream)
    payload = {
        "featureSet": "Alpha158",
        "model": "LightGBM Regression",
        "label": "next-morning sell VWAP / next-afternoon buy VWAP - 1",
        "segments": _segments_record(segments),
        "metrics": metrics,
        "topK": top_k,
        "rows": len(scored),
        "labeledRows": len(evaluation),
        "predictionDays": int(top["datetime"].nunique()),
    }
    (artifact_dir / "metrics.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return payload


def _persist_training_artifacts(
    model,
    scored: pd.DataFrame,
    artifact_dir: Path,
    segments: ModelSegments,
    top_k: int,
    training_mode: str,
    train_rows: int,
    valid_rows: int,
    feature_count: int,
    feature_batch_days: int,
) -> dict[str, object]:
    evaluation = scored.dropna(subset=["score", "label"])
    metrics = _cross_sectional_metrics(evaluation)
    top = (
        scored.dropna(subset=["score"])
        .reset_index()
        .sort_values(
            ["datetime", "score", "instrument"], ascending=[True, False, True]
        )
        .groupby("datetime", sort=True)
        .head(top_k)
    )
    LOGGER.info(
        "测试集评估完成：有效样本=%d；IC均值=%s；Rank IC均值=%s",
        len(evaluation),
        metrics["icMean"],
        metrics["rankIcMean"],
    )
    LOGGER.info(
        "写入训练制品：模式=%s；目录=%s",
        training_mode,
        artifact_dir,
    )
    artifact_dir.mkdir(parents=True, exist_ok=True)
    scored.reset_index().to_parquet(artifact_dir / "test_predictions.parquet", index=False)
    top.to_parquet(artifact_dir / "top5.parquet", index=False)
    with (artifact_dir / "model.pkl").open("wb") as stream:
        pickle.dump(model, stream)
    payload = {
        "featureSet": "Alpha158",
        "model": "LightGBM Regression",
        "label": "next-morning sell VWAP / next-afternoon buy VWAP - 1",
        "segments": _segments_record(segments),
        "metrics": metrics,
        "topK": top_k,
        "rows": len(scored),
        "labeledRows": len(evaluation),
        "predictionDays": int(top["datetime"].nunique()),
        "trainingMode": training_mode,
        "trainRows": train_rows,
        "validRows": valid_rows,
        "featureCount": feature_count,
        "featureBatchDays": feature_batch_days,
    }
    (artifact_dir / "metrics.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return payload


def predict_alpha158_lightgbm(
    qlib_dir: Path,
    model_dir: Path,
    prediction_date: date,
    eligible_instruments: set[str] | None = None,
) -> dict[str, object]:
    """Run one deterministic cross-sectional prediction with an existing model."""
    import qlib
    from qlib.constant import REG_CN
    from qlib.contrib.data.handler import Alpha158
    from qlib.data.dataset import DatasetH
    from qlib.data.dataset.handler import DataHandlerLP

    set_log_context(progress="1/4", target="模型配置")
    LOGGER.info("读取模型配置和交易日历")
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    segments = metrics["segments"]
    calendar = pd.to_datetime(
        (qlib_dir / "calendars" / "day.txt").read_text(encoding="utf-8").splitlines()
    )
    target = pd.Timestamp(prediction_date)
    if target not in calendar:
        raise ValueError(f"prediction date is absent from Qlib calendar: {prediction_date}")

    set_log_context(progress="2/4", target="Alpha158")
    LOGGER.info("初始化 Qlib 并生成特征")
    from stock_trading.research.batched_model import (
        load_alpha158_features_for_date,
        total_physical_memory_bytes,
        use_batched_training,
    )

    if use_batched_training(total_physical_memory_bytes()):
        features = load_alpha158_features_for_date(qlib_dir, prediction_date)
    else:
        qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
        handler = Alpha158(
            instruments="all",
            start_time=pd.Timestamp(calendar.min()).date(),
            end_time=prediction_date,
            fit_start_time=date.fromisoformat(segments["train_start"]),
            fit_end_time=date.fromisoformat(segments["train_end"]),
            label=(["$label"], ["LABEL0"]),
        )
        dataset = DatasetH(
            handler=handler,
            segments={"prediction": (prediction_date, prediction_date)},
        )
        features = dataset.prepare(
            "prediction", col_set="feature", data_key=DataHandlerLP.DK_I
        )
    if features.empty:
        raise ValueError(f"no Alpha158 features for prediction date: {prediction_date}")
    LOGGER.info(
        "预测特征准备完成：日期=%s；股票数=%d；特征数=%d",
        prediction_date,
        len(features),
        features.shape[1],
    )
    set_log_context(progress="3/4", target="横截面评分")
    LOGGER.info("加载模型并计算评分")
    with (model_dir / "model.pkl").open("rb") as stream:
        model = pickle.load(stream)
    model_columns = _model_feature_names(model)
    if model_columns:
        missing = sorted(set(model_columns) - set(features.columns))
        if missing:
            raise ValueError(f"prediction features are missing model columns: {missing[:5]}")
        features = features.loc[:, model_columns]
    scored = pd.Series(
        _model_predict(model, features), index=features.index, name="score"
    ).dropna()
    scored.index.names = ["datetime", "instrument"]
    if eligible_instruments is not None:
        allowed = {value.upper() for value in eligible_instruments}
        instruments = scored.index.get_level_values("instrument").astype(str).str.upper()
        scored = scored[instruments.isin(allowed)]
    if scored.empty:
        raise ValueError(f"no eligible prediction instruments for date: {prediction_date}")
    ranking = (
        scored.reset_index()
        .sort_values(
            ["datetime", "score", "instrument"], ascending=[True, False, True]
        )
        .reset_index(drop=True)
    )
    ranking["rank"] = range(1, len(ranking) + 1)
    ranking["label"] = float("nan")
    set_log_context(progress="4/4", target=str(prediction_date))
    LOGGER.info("生成完整横截面排名并写入制品：股票数=%d", len(ranking))
    output_dir = model_dir / "predictions"
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / f"ranking-{prediction_date.isoformat()}.parquet"
    temporary = output.with_suffix(".parquet.tmp")
    try:
        ranking.to_parquet(temporary, index=False)
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    return {
        "model": metrics["model"],
        "featureSet": metrics["featureSet"],
        "predictionDate": prediction_date.isoformat(),
        "universeRows": int(len(scored)),
        "rankingRows": int(len(ranking)),
        "path": str(output),
        "topCandidates": [
            {
                "instrument": str(row.instrument),
                "rank": int(row.rank),
                "score": float(row.score),
            }
            for row in ranking.head(5).itertuples(index=False)
        ],
    }


def _model_feature_names(model) -> list[str]:
    names = getattr(model, "feature_name_", None)
    if names is not None:
        return list(names)
    feature_name = getattr(model, "feature_name", None)
    return list(feature_name()) if callable(feature_name) else []


def _model_predict(model, features: pd.DataFrame):
    best_iteration = getattr(model, "best_iteration_", None)
    if best_iteration is None:
        best_iteration = getattr(model, "best_iteration", None)
    if best_iteration:
        return model.predict(features, num_iteration=best_iteration)
    return model.predict(features)


def _cross_sectional_metrics(frame: pd.DataFrame) -> dict[str, float | int]:
    daily = frame.groupby(level="datetime").apply(_daily_correlations, include_groups=False)
    return {
        "dailySamples": int(len(daily)),
        "validIcDays": int(daily["ic"].notna().sum()),
        "validRankIcDays": int(daily["rankIc"].notna().sum()),
        "constantScoreDays": int(daily["constantScore"].sum()),
        "icMean": float(daily["ic"].mean()),
        "icStd": float(daily["ic"].std()),
        "rankIcMean": float(daily["rankIc"].mean()),
        "rankIcStd": float(daily["rankIc"].std()),
    }


def _daily_correlations(group: pd.DataFrame) -> pd.Series:
    constant_score = group["score"].nunique() < 2
    constant_label = group["label"].nunique() < 2
    if constant_score or constant_label:
        return pd.Series(
            {
                "ic": float("nan"),
                "rankIc": float("nan"),
                "constantScore": constant_score,
            }
        )
    return pd.Series(
        {
            "ic": group["score"].corr(group["label"], method="pearson"),
            "rankIc": group["score"].corr(group["label"], method="spearman"),
            "constantScore": False,
        }
    )


def _segments_record(segments: ModelSegments) -> dict[str, str]:
    return {key: value.isoformat() for key, value in asdict(segments).items()}


def _lightgbm_progress_callback():
    """Build a throttled LightGBM callback that reports validation progress."""

    def callback(environment) -> None:
        current = environment.iteration + 1
        total = environment.end_iteration
        if current != 1 and current != total and current % 25 != 0:
            return
        metrics = "; ".join(
            f"{item[0]}:{item[1]}={float(item[2]):.8f}"
            for item in environment.evaluation_result_list
        )
        set_log_context(progress=f"{current}/{total}", target="LightGBM")
        LOGGER.info("训练指标：%s", metrics)

    callback.order = 20
    callback.before_iteration = False
    return callback


def automatic_model_segments(
    calendar_path: Path,
    split: tuple[Decimal, Decimal, Decimal] | None = None,
) -> ModelSegments:
    """按指定比例切分连续交易日；省略比例时使用 70%/15%/15%。"""
    calendar = [
        date.fromisoformat(value)
        for value in calendar_path.read_text(encoding="utf-8").splitlines()
        if value.strip()
    ]
    if len(calendar) < 120:
        raise ValueError(f"自动切分至少需要 120 个交易日，当前={len(calendar)}")
    ratios = split or (Decimal("70"), Decimal("15"), Decimal("15"))
    if any(not ratio.is_finite() or ratio <= 0 for ratio in ratios):
        raise ValueError("训练、验证和测试比例必须全部大于 0")
    total = sum(ratios, Decimal("0"))
    train_end = int(Decimal(len(calendar)) * ratios[0] / total) - 1
    valid_end = int(
        Decimal(len(calendar)) * (ratios[0] + ratios[1]) / total
    ) - 1
    train_days = train_end + 1
    valid_days = valid_end - train_end
    test_days = len(calendar) - valid_end - 1
    if min(train_days, valid_days, test_days) <= 0:
        raise ValueError(
            "训练比例无法为每个区间分配交易日；请调整训练、验证和测试比例"
        )
    return ModelSegments(
        train_start=calendar[0],
        train_end=calendar[train_end],
        valid_start=calendar[train_end + 1],
        valid_end=calendar[valid_end],
        test_start=calendar[valid_end + 1],
        test_end=calendar[-1],
    )

# AI_GENERATE_END --------------------
