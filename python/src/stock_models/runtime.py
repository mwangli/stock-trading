# AI_GENERATE_START --
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from stock_models.config import AppConfig


@dataclass(frozen=True)
class RunContext:
    """一次不可变离线训练运行对应的目录集合。"""

    run_id: str
    run_dir: Path
    logs_dir: Path
    outputs_dir: Path


def create_run_context(config: AppConfig, prefix: str) -> RunContext:
    """创建隔离的 Run 目录，不读取或修改生产模型激活状态。"""

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_id = f"{prefix}-{timestamp}-{uuid4().hex[:8]}"
    run_dir = config.runtime.runs_dir / run_id
    logs_dir = run_dir / "logs"
    outputs_dir = run_dir / "outputs"
    logs_dir.mkdir(parents=True, exist_ok=False)
    outputs_dir.mkdir(parents=True, exist_ok=False)
    return RunContext(run_id, run_dir, logs_dir, outputs_dir)
# AI_GENERATE_END --
