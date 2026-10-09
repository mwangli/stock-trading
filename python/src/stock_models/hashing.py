# AI_GENERATE_START --
from __future__ import annotations

import hashlib
from pathlib import Path


def sha256_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    """以分块方式计算文件 SHA-256，避免大模型文件一次性进入内存。"""

    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()
# AI_GENERATE_END --
