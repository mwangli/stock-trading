# AI_GENERATE_START -----
from __future__ import annotations

from datetime import UTC, date, datetime
from zoneinfo import ZoneInfo


SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")


def shanghai_now() -> datetime:
    """返回带时区的上海当前时间，供交易业务统一确定日期和时间。"""
    return datetime.now(SHANGHAI_ZONE)


def shanghai_today() -> date:
    """返回上海时区业务日期。"""
    return shanghai_now().date()


def utc_now() -> datetime:
    """返回带 UTC 时区的当前时间，供持久化时间戳使用。"""
    return datetime.now(UTC)
# AI_GENERATE_END -----
