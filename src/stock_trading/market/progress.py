# AI_GENERATE_START ---------
from __future__ import annotations

import time
from collections import deque
from dataclasses import dataclass, field
from multiprocessing import Array, Value
from types import TracebackType
from typing import Any, Self

from stock_trading.logging_utils import (
    PROGRESS_MESSAGE_LIMIT,
    bind_progress_context,
    restore_progress_context,
    start_progress_renderer,
    stop_progress_renderer,
)

ESTIMATE_WINDOWS_SECONDS = (1, 5, 10, 30, 60, 5 * 60)
MAX_ESTIMATE_WINDOW_SECONDS = ESTIMATE_WINDOWS_SECONDS[-1]


@dataclass
class CollectionProgress:
    """Share collection progress with worker loggers and estimate remaining time."""

    total_count: int
    initial_completed: int = 0
    _completed: Any = field(init=False)
    _remaining_seconds: Any = field(init=False)
    _message: Any = field(init=False)
    _previous_context: Any = field(init=False)
    _renderer_started: bool = field(init=False, default=False)
    _estimate_started_at: float = field(init=False, default=0.0)
    _samples: deque[tuple[float, int]] = field(init=False, default_factory=deque)

    def __enter__(self) -> Self:
        initial = min(self.total_count, max(0, self.initial_completed))
        self._completed = Value("q", initial, lock=True)
        initial_remaining = 0 if initial >= self.total_count else -1
        self._remaining_seconds = Value("q", initial_remaining, lock=True)
        self._message = Array("u", PROGRESS_MESSAGE_LIMIT, lock=True)
        with self._message.get_lock():
            self._message.get_obj().value = "正在准备研究数据采集"
        self._previous_context = bind_progress_context(
            self._completed,
            self._remaining_seconds,
            self.total_count,
            self._message,
        )
        self._renderer_started = start_progress_renderer()
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        if self._renderer_started:
            stop_progress_renderer()
        restore_progress_context(self._previous_context)

    @property
    def worker_context(self) -> tuple[Any, Any, int, Any]:
        return (
            self._completed,
            self._remaining_seconds,
            self.total_count,
            self._message,
        )

    def begin_estimate(self, completed: int) -> None:
        """Start collection-speed sampling after the file-check stage."""
        processed = min(self.total_count, max(0, completed))
        now = time.monotonic()
        self._estimate_started_at = now
        self._samples.clear()
        self._samples.append((now, processed))
        self._set_values(processed, 0 if processed >= self.total_count else -1)

    def update_check(self, completed: int) -> None:
        """Advance file-check work while keeping ETA unavailable."""
        processed = min(self.total_count, max(0, completed))
        self._set_values(processed, 0 if processed >= self.total_count else -1)

    def update(self, completed: int) -> None:
        now = time.monotonic()
        processed = min(self.total_count, max(0, completed))
        self._samples.append((now, processed))
        cutoff = now - MAX_ESTIMATE_WINDOW_SECONDS
        while len(self._samples) > 2 and self._samples[1][0] <= cutoff:
            self._samples.popleft()

        remaining_seconds = self._estimate_remaining(now, processed)
        self._set_values(processed, remaining_seconds)

    def _set_values(self, completed: int, remaining_seconds: int) -> None:
        with self._completed.get_lock():
            self._completed.value = completed
        with self._remaining_seconds.get_lock():
            self._remaining_seconds.value = remaining_seconds

    def _estimate_remaining(self, now: float, processed: int) -> int:
        if processed >= self.total_count:
            return 0
        elapsed_since_start = now - self._estimate_started_at
        available_windows = [
            window
            for window in ESTIMATE_WINDOWS_SECONDS
            if window <= elapsed_since_start
        ]
        if not available_windows:
            return -1
        window = available_windows[-1]
        target_time = now - window
        sample_time, sample_processed = min(
            self._samples,
            key=lambda sample: abs(sample[0] - target_time),
        )
        elapsed = now - sample_time
        progressed = processed - sample_processed
        if elapsed <= 0 or progressed <= 0:
            return -1
        remaining = max(0, self.total_count - processed)
        return max(0, round(remaining * elapsed / progressed))
# AI_GENERATE_END ---------
