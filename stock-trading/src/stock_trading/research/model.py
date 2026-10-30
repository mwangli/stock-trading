# AI_GENERATE_START ------
from __future__ import annotations

import json
import pickle
from dataclasses import asdict, dataclass
from datetime import date
from pathlib import Path

import pandas as pd


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

    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=2)
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
    train = dataset.prepare(
        "train", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    )
    valid = dataset.prepare(
        "valid", col_set=["feature", "label"], data_key=DataHandlerLP.DK_L
    )
    test_features = dataset.prepare(
        "test", col_set="feature", data_key=DataHandlerLP.DK_I
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
    model.fit(
        train["feature"],
        train["label"].iloc[:, 0],
        eval_X=valid["feature"],
        eval_y=valid["label"].iloc[:, 0],
        callbacks=[lgb.early_stopping(30, verbose=False), lgb.log_evaluation(0)],
    )
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
        .sort_values(["datetime", "score"], ascending=[True, False])
        .groupby("datetime", sort=True)
        .head(top_k)
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
    }
    (artifact_dir / "metrics.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return payload


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
# AI_GENERATE_END ------
