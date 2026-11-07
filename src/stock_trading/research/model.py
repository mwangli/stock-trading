# AI_GENERATE_START ----------------------------------------
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

from stock_trading.utils.logging_utils import set_log_context
from stock_trading.research.model_config import ModelSpec
from stock_trading.research.qlib_contract import (
    TRADING_TIME_CONTRACT, expected_label_contract, trading_time_contract,
)
from stock_trading.utils.time_utils import utc_now
from stock_trading.market.trade_calendar import shift_trade_date

LOGGER = logging.getLogger(__name__)
LIGHTGBM_NUM_BOOST_ROUND = 300
LIGHTGBM_EARLY_STOPPING_ROUNDS = 30
MODEL_LABEL_CONTRACT = expected_label_contract(None)
MODEL_LABEL_DESCRIPTION = (
    "D+2 13:00-14:00 adjusted sell VWAP / "
    "D+1 14:00-15:00 adjusted buy VWAP - 1; signal after D close"
)


def lightgbm_parameters() -> dict[str, object]:
    """返回单次和分批训练共享的 LightGBM 业务参数。"""
    return {
        "objective": "regression",
        "metric": "l2",
        "learning_rate": 0.05,
        "num_leaves": 31,
        "max_depth": 6,
        "sample_fraction": 0.8,
        "feature_fraction": 0.8,
        "lambda_l1": 1.0,
        "lambda_l2": 1.0,
        "threads": 2,
        "seed": 20261030,
        "verbosity": -1,
    }


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
    feature_batch_days: int = 60,
    checkpoint_dir: Path | None = None,
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

    work_dir = checkpoint_dir or artifact_dir / ".feature-batches"
    result = train_alpha158_lightgbm_batched(
        qlib_dir,
        work_dir,
        segments,
        _lightgbm_progress_callback(),
        feature_batch_days,
        lightgbm_parameters(),
    )
    payload = _persist_training_artifacts(
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
    if checkpoint_dir is None:
        shutil.rmtree(work_dir, ignore_errors=True)
    return payload


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
    from stock_trading.research.features import MorningAlpha158
    from qlib.data.dataset import DatasetH
    from qlib.data.dataset.handler import DataHandlerLP

    set_log_context(step="1/6", progress="-/-", target="Qlib")
    LOGGER.info("初始化 Qlib：目录=%s", qlib_dir)
    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
    set_log_context(step="2/6", progress="-/-", target="Alpha158")
    LOGGER.info("构建特征处理器")
    handler = MorningAlpha158(
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
    set_log_context(step="3/6", progress="-/-", target="训练数据")
    LOGGER.info("准备训练、验证和测试数据")
    train = dataset.prepare(
        "train", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    ).sort_index()
    valid = dataset.prepare(
        "valid", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    ).sort_index()
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
    parameters = lightgbm_parameters()
    model = lgb.LGBMRegressor(
        objective=parameters["objective"],
        n_estimators=LIGHTGBM_NUM_BOOST_ROUND,
        learning_rate=parameters["learning_rate"],
        num_leaves=parameters["num_leaves"],
        max_depth=parameters["max_depth"],
        subsample=parameters["sample_fraction"],
        colsample_bytree=parameters["feature_fraction"],
        lambda_l1=parameters["lambda_l1"],
        lambda_l2=parameters["lambda_l2"],
        n_jobs=parameters["threads"],
        random_state=parameters["seed"],
        verbosity=parameters["verbosity"],
    )
    set_log_context(
        step="4/6", progress=f"0/{LIGHTGBM_NUM_BOOST_ROUND}", target="LightGBM"
    )
    LOGGER.info(
        "开始模型迭代：最大迭代=%d；早停轮数=%d",
        LIGHTGBM_NUM_BOOST_ROUND,
        LIGHTGBM_EARLY_STOPPING_ROUNDS,
    )
    model.fit(
        train["feature"],
        train["label"].iloc[:, 0],
        eval_set=[(valid["feature"], valid["label"].iloc[:, 0])],
        eval_names=["valid"],
        eval_metric="l2",
        callbacks=[
            lgb.early_stopping(LIGHTGBM_EARLY_STOPPING_ROUNDS, verbose=False),
            _lightgbm_progress_callback(),
        ],
    )
    LOGGER.info("LightGBM 训练完成：最佳迭代=%s", getattr(model, "best_iteration_", None))
    set_log_context(step="5/6", progress="-/-", target="测试集评分")
    LOGGER.info("生成测试集评分和 Top %d", top_k)
    predictions = pd.Series(
        model.predict(test_features), index=test_features.index, name="score"
    )
    labels = dataset.prepare(
        "test", col_set="label", data_key=DataHandlerLP.DK_R
    ).iloc[:, 0].rename("label")
    scored = pd.concat([predictions, labels], axis=1)
    scored.index.names = ["datetime", "instrument"]
    set_log_context(step="6/6", progress="-/-", target=str(artifact_dir))
    return _persist_training_artifacts(
        model,
        scored,
        artifact_dir,
        segments,
        top_k,
        training_mode="single-pass",
        train_rows=len(train),
        valid_rows=len(valid),
        feature_count=train["feature"].shape[1],
        feature_batch_days=None,
    )


def train_configured_model(
    spec: ModelSpec,
    qlib_dir: Path,
    artifact_dir: Path,
    segments: ModelSegments,
    top_k: int = 5,
) -> dict[str, object]:
    """通过统一数据集接口训练配置指定的 LightGBM 或 XGBoost。"""
    import qlib
    from qlib.constant import REG_CN
    from qlib.data.dataset import DatasetH
    from qlib.data.dataset.handler import DataHandlerLP

    handler_type = _configured_handler(spec)
    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
    handler = handler_type(
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
    train = dataset.prepare(
        "train", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    )
    valid = dataset.prepare(
        "valid", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    )
    test_features = dataset.prepare(
        "test", col_set="feature", data_key=DataHandlerLP.DK_I
    )
    model = _configured_estimator(spec)
    set_log_context(step="4/6", progress="0/300", target=spec.algorithm)
    train_target = _training_target(spec, train["label"].iloc[:, 0])
    valid_target = _training_target(spec, valid["label"].iloc[:, 0])
    if spec.algorithm == "LightGBM":
        import lightgbm as lgb

        model.fit(
            train["feature"],
            train_target,
            eval_set=[(valid["feature"], valid_target)],
            eval_names=["valid"],
            eval_metric="l1" if spec.training_target == "winsorized_return" else "l2",
            callbacks=[
                lgb.early_stopping(LIGHTGBM_EARLY_STOPPING_ROUNDS, verbose=False),
                _lightgbm_progress_callback(),
            ],
        )
    elif spec.algorithm == "XGBoostRanker":
        model.fit(
            train["feature"],
            train_target,
            group=_date_groups(train_target),
            eval_set=[(valid["feature"], valid_target)],
            eval_group=[_date_groups(valid_target)],
            verbose=False,
        )
    else:
        model.fit(
            train["feature"],
            train_target,
            eval_set=[(valid["feature"], valid_target)],
            verbose=False,
        )
    predictions = pd.Series(
        _model_predict(model, test_features),
        index=test_features.index,
        name="score",
    )
    labels = dataset.prepare(
        "test", col_set="label", data_key=DataHandlerLP.DK_R
    ).iloc[:, 0].rename("label")
    scored = pd.concat([predictions, labels], axis=1)
    scored.index.names = ["datetime", "instrument"]
    return _persist_training_artifacts(
        model,
        scored,
        artifact_dir,
        segments,
        top_k,
        training_mode="single-pass",
        train_rows=len(train),
        valid_rows=len(valid),
        feature_count=train["feature"].shape[1],
        feature_batch_days=None,
        spec=spec,
    )


def _configured_handler(spec: ModelSpec):
    from stock_trading.research.features import (
        DailyAlpha158,
        FullDayMinute1Alpha158,
        MorningAlpha158,
    )

    if spec.feature_frequency == "daily":
        return DailyAlpha158
    if spec.feature_frequency == "minute1":
        return FullDayMinute1Alpha158
    return MorningAlpha158


def _configured_estimator(spec: ModelSpec):
    parameters = dict(spec.algorithm_parameters)
    if spec.algorithm == "LightGBM":
        import lightgbm as lgb

        return lgb.LGBMRegressor(
            objective=parameters.pop("objective", "regression"),
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
            early_stopping_rounds=LIGHTGBM_EARLY_STOPPING_ROUNDS,
            **parameters,
        )
    if spec.algorithm == "XGBoostRanker":
        import xgboost as xgb

        return xgb.XGBRanker(
            objective="rank:pairwise",
            eval_metric="ndcg@5",
            n_jobs=2,
            random_state=20261030,
            early_stopping_rounds=LIGHTGBM_EARLY_STOPPING_ROUNDS,
            **parameters,
        )
    raise ValueError(f"不支持的模型算法：{spec.algorithm}")


def _training_target(spec: ModelSpec, labels: pd.Series) -> pd.Series:
    values = labels.astype(float).copy()
    if spec.training_target == "raw_return":
        return values
    if spec.training_target == "winsorized_return":
        return values.groupby(level="datetime", group_keys=False).transform(
            lambda group: group.clip(group.quantile(0.01), group.quantile(0.99))
        )
    if spec.training_target == "cross_sectional_rank":
        ranks = values.groupby(level="datetime", group_keys=False).rank(
            method="average", pct=True
        )
        return ranks.mul(5).sub(1e-12).astype(int).clip(0, 4)
    raise ValueError(f"不支持的训练标签变换：{spec.training_target}")


def _date_groups(labels: pd.Series) -> list[int]:
    return labels.groupby(level="datetime", sort=False).size().astype(int).tolist()


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
    feature_batch_days: int | None,
    spec: ModelSpec | None = None,
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
        "modelId": spec.model_id if spec else "legacy",
        "algorithm": spec.algorithm if spec else "LightGBM",
        "dataFeature": spec.feature_frequency if spec else "minute5",
        "labelHorizon": spec.label_horizon if spec else 1,
        "businessDescription": (
            spec.business_description if spec else MODEL_LABEL_DESCRIPTION
        ),
        "configSummary": (
            {
                "algorithmParameters": spec.algorithm_parameters,
                "requiredFields": spec.required_fields,
                "trainingTarget": spec.training_target,
                "trainingWindowDays": spec.training_window_days,
            }
            if spec
            else {}
        ),
        "featureSet": spec.feature_set if spec else "MorningAlpha158",
        "model": (
            "XGBoost Pairwise Ranking"
            if spec and spec.algorithm == "XGBoostRanker"
            else f"{spec.algorithm} Regression"
            if spec
            else "LightGBM Regression"
        ),
        "labelContract": expected_label_contract(spec),
        "timeContractVersion": TRADING_TIME_CONTRACT,
        "timeContract": trading_time_contract(spec),
        "labelPurgedTradeDays": 1 + (spec.label_horizon if spec else 1),
        "label": spec.label_description if spec else MODEL_LABEL_DESCRIPTION,
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
    calendar_dir: Path | None = None,
) -> dict[str, object]:
    """Run one deterministic cross-sectional prediction with an existing model."""
    from stock_trading.research.features import MorningAlpha158

    set_log_context(step="1/4", progress="-/-", target="模型配置")
    LOGGER.info("读取模型配置和交易日历")
    metrics, calendar = _prediction_context(qlib_dir, model_dir, prediction_date)
    segments = metrics["segments"]

    set_log_context(step="2/4", progress="-/-", target="Alpha158")
    LOGGER.info("初始化 Qlib 并生成特征")
    from stock_trading.research.batched_model import (
        load_alpha158_features_for_date,
        total_physical_memory_bytes,
        use_batched_training,
    )

    if use_batched_training(total_physical_memory_bytes()):
        features = load_alpha158_features_for_date(qlib_dir, prediction_date)
    else:
        features = _prediction_features(
            MorningAlpha158, qlib_dir, prediction_date, segments, calendar
        )
    if features.empty:
        raise ValueError(f"no Alpha158 features for prediction date: {prediction_date}")
    LOGGER.info(
        "预测特征准备完成：日期=%s；股票数=%d；特征数=%d",
        prediction_date,
        len(features),
        features.shape[1],
    )
    set_log_context(step="3/4", progress="-/-", target="横截面评分")
    LOGGER.info("加载模型并计算评分")
    scored = _prediction_scores(model_dir, features, eligible_instruments)
    if scored.empty:
        raise ValueError(f"no eligible prediction instruments for date: {prediction_date}")
    set_log_context(step="4/4", progress="-/-", target=str(prediction_date))
    LOGGER.info("生成完整横截面排名并写入制品：股票数=%d", len(scored))
    return _persist_prediction(
        scored, metrics, model_dir, prediction_date, calendar_dir
    )


def predict_configured_model(
    spec: ModelSpec,
    qlib_dir: Path,
    model_dir: Path,
    prediction_date: date,
    eligible_instruments: set[str] | None = None,
    calendar_dir: Path | None = None,
) -> dict[str, object]:
    """使用配置指定的特征处理器和已训练模型生成单日完整排名。"""

    metrics, calendar = _prediction_context(qlib_dir, model_dir, prediction_date)
    segments = metrics["segments"]
    features = _prediction_features(
        _configured_handler(spec), qlib_dir, prediction_date, segments, calendar
    )
    if features.empty:
        raise ValueError(f"目标日期没有可用模型特征：{prediction_date}")
    scored = _prediction_scores(model_dir, features, eligible_instruments)
    if scored.empty:
        raise ValueError(f"目标日期没有可排名股票：{prediction_date}")
    return {
        "modelId": spec.model_id,
        **_persist_prediction(scored, metrics, model_dir, prediction_date, calendar_dir),
    }


def _prediction_features(
    handler_type,
    qlib_dir: Path,
    prediction_date: date,
    segments: dict[str, str],
    calendar: pd.DatetimeIndex,
) -> pd.DataFrame:
    """共用单日特征数据集构建；低内存入口仍由调用方单独选择。"""
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
    dataset = DatasetH(
        handler=handler,
        segments={"prediction": (prediction_date, prediction_date)},
    )
    return dataset.prepare(
        "prediction", col_set="feature", data_key=DataHandlerLP.DK_I
    )


def _prediction_context(
    qlib_dir: Path, model_dir: Path, prediction_date: date
) -> tuple[dict[str, object], pd.DatetimeIndex]:
    """读取两条预测入口共用的模型时间合同和 Qlib 日历。"""
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    if metrics.get("timeContractVersion") != TRADING_TIME_CONTRACT:
        raise ValueError("模型时间合同已过期；请先重新准备数据并训练模型")
    calendar = pd.to_datetime(
        (qlib_dir / "calendars" / "day.txt").read_text(encoding="utf-8").splitlines()
    )
    target = pd.Timestamp(prediction_date)
    if target not in calendar:
        raise ValueError(f"prediction date is absent from Qlib calendar: {prediction_date}")
    return metrics, calendar


def _prediction_scores(
    model_dir: Path,
    features: pd.DataFrame,
    eligible_instruments: set[str] | None,
) -> pd.Series:
    """统一特征对齐、评分及候选过滤，不改变各入口的特征加载策略。"""
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
    return scored


def _persist_prediction(
    scored: pd.Series,
    metrics: dict[str, object],
    model_dir: Path,
    prediction_date: date,
    calendar_dir: Path | None,
) -> dict[str, object]:
    """为所有模型写入同一格式的完整排名，并原子替换制品。"""
    ranking = (
        scored.reset_index()
        .sort_values(
            ["datetime", "score", "instrument"], ascending=[True, False, True]
        )
        .reset_index(drop=True)
    )
    ranking["rank"] = range(1, len(ranking) + 1)
    ranking["label"] = float("nan")
    ranking["execution_date"] = shift_trade_date(prediction_date, 1, calendar_dir).isoformat()
    ranking["generated_at"] = utc_now().isoformat()
    ranking["contract_version"] = TRADING_TIME_CONTRACT
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
    names = getattr(model, "feature_names_in_", None)
    if names is not None:
        return list(names)
    names = getattr(model, "feature_name_", None)
    if names is not None:
        return list(names)
    feature_name = getattr(model, "feature_name", None)
    return list(feature_name()) if callable(feature_name) else []


def _model_predict(model, features: pd.DataFrame):
    if model.__class__.__module__.startswith("xgboost"):
        return model.predict(features)
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
    maximum_days: int | None = None,
    label_horizon: int = 1,
) -> ModelSegments:
    """按比例切分，并剔除标签结束日越过分段边界的样本。"""
    calendar = [
        date.fromisoformat(value)
        for value in calendar_path.read_text(encoding="utf-8").splitlines()
        if value.strip()
    ]
    if maximum_days is not None:
        if maximum_days <= 0:
            raise ValueError("训练窗口交易日数必须大于 0")
        calendar = calendar[-maximum_days:]
    if len(calendar) < 63:
        raise ValueError(f"自动切分至少需要 63 个交易日，当前={len(calendar)}")
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
    label_offset = label_horizon + 1
    if label_horizon <= 0 or min(train_days, valid_days, test_days) <= label_offset:
        raise ValueError("剔除跨区间或未成熟标签后没有足够样本，请扩大日期范围或调整比例")
    return ModelSegments(
        train_start=calendar[0],
        train_end=calendar[train_end - label_offset],
        valid_start=calendar[train_end + 1],
        valid_end=calendar[valid_end - label_offset],
        test_start=calendar[valid_end + 1],
        test_end=calendar[-1 - label_offset],
    )

# AI_GENERATE_END ----------------------------------------
