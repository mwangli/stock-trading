# AI_GENERATE_START ---
from __future__ import annotations

import time
from collections import deque
from dataclasses import dataclass, field
from multiprocessing import Value
from types import TracebackType
from typing import Any, Self

from stock_trading.logging_utils import bind_progress_context, clear_progress_context

MIN_ESTIMATE_SECONDS = 5 * 60
ESTIMATE_WINDOW_SECONDS = 10 * 60


@dataclass
class ResearchProgress:
    """Share collection progress with worker loggers and estimate remaining time."""

    total_count: int
    skipped: int
    _completed: Any = field(init=False)
    _remaining_seconds: Any = field(init=False)
    _started_at: float = field(init=False, default=0.0)
    _samples: deque[tuple[float, int]] = field(init=False, default_factory=deque)

    def __enter__(self) -> Self:
        initial = min(self.total_count, max(0, self.skipped))
        self._completed = Value("q", initial, lock=True)
        initial_remaining = 0 if initial >= self.total_count else -1
        self._remaining_seconds = Value("q", initial_remaining, lock=True)
        self._started_at = time.monotonic()
        self._samples.append((self._started_at, initial))
        bind_progress_context(
            self._completed,
            self._remaining_seconds,
            self.total_count,
        )
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        clear_progress_context()

    @property
    def worker_context(self) -> tuple[Any, Any, int]:
        return self._completed, self._remaining_seconds, self.total_count

    def update(self, completed: int) -> None:
        now = time.monotonic()
        processed = min(self.total_count, self.skipped + completed)
        self._samples.append((now, processed))
        while (
            len(self._samples) > 1
            and now - self._samples[0][0] > ESTIMATE_WINDOW_SECONDS
        ):
            self._samples.popleft()

        remaining_seconds = self._estimate_remaining(now, processed)
        with self._completed.get_lock():
            self._completed.value = processed
        with self._remaining_seconds.get_lock():
            self._remaining_seconds.value = remaining_seconds

    def _estimate_remaining(self, now: float, processed: int) -> int:
        if processed >= self.total_count:
            return 0
        if now - self._started_at < MIN_ESTIMATE_SECONDS:
            return -1
        sample_time, sample_processed = self._samples[0]
        elapsed = now - sample_time
        progressed = processed - sample_processed
        if elapsed < MIN_ESTIMATE_SECONDS or progressed <= 0:
            return -1
        remaining = max(0, self.total_count - processed)
        return max(0, round(remaining * elapsed / progressed))
# AI_GENERATE_END ---
