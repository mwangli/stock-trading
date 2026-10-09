# AI_GENERATE_START -----
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from stock_models.artifact import build_artifact
from stock_models.config import load_config
from stock_models.data.snapshot import validate_snapshot
from stock_models.lstm.trainer import train_lstm
from stock_models.runtime import create_run_context
from stock_models.sentiment.trainer import train_sentiment


def build_parser() -> argparse.ArgumentParser:
    """创建当前已实现的离线模型命令行入口。"""

    parser = argparse.ArgumentParser(prog="stock-models")
    subparsers = parser.add_subparsers(dest="command", required=True)

    inspect_config = subparsers.add_parser("inspect-config")
    inspect_config.add_argument("--config", type=Path, required=True)

    validate_data = subparsers.add_parser("validate-data")
    validate_data.add_argument("--manifest", type=Path, required=True)

    create_run = subparsers.add_parser("create-run")
    create_run.add_argument("--config", type=Path, required=True)
    create_run.add_argument("--prefix", required=True)

    train_lstm_parser = subparsers.add_parser("train-lstm")
    train_lstm_parser.add_argument("--config", type=Path, required=True)
    train_lstm_parser.add_argument("--manifest", type=Path, required=True)

    train_sentiment_parser = subparsers.add_parser("train-sentiment")
    train_sentiment_parser.add_argument("--config", type=Path, required=True)
    train_sentiment_parser.add_argument("--manifest", type=Path, required=True)

    artifact = subparsers.add_parser("build-artifact")
    artifact.add_argument("--output-dir", type=Path, required=True)
    artifact.add_argument("--model", type=Path, required=True)
    artifact.add_argument("--metadata", type=Path, required=True)
    artifact.add_argument("--metrics", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """执行一个已实现的离线操作；未完成的训练能力不会提供占位命令。"""

    # 每个命令只完成一个可审计动作，避免训练、发布和激活隐式串联。
    args = build_parser().parse_args(argv)
    if args.command == "inspect-config":
        config = load_config(args.config)
        print(json.dumps(config.model_dump(mode="json"), ensure_ascii=False, indent=2))
        return 0
    if args.command == "validate-data":
        manifest = validate_snapshot(args.manifest)
        print(manifest.model_dump_json(indent=2))
        return 0
    if args.command == "create-run":
        context = create_run_context(load_config(args.config), args.prefix)
        print(context.run_id)
        return 0
    if args.command == "train-lstm":
        run_dir = train_lstm(load_config(args.config), args.manifest)
        print(run_dir)
        return 0
    if args.command == "train-sentiment":
        run_dir = train_sentiment(load_config(args.config), args.manifest)
        print(run_dir)
        return 0
    if args.command == "build-artifact":
        artifact_dir = build_artifact(
            args.output_dir, args.model, args.metadata, args.metrics
        )
        print(artifact_dir)
        return 0
    raise AssertionError(f"unsupported command: {args.command}")
# AI_GENERATE_END -----
