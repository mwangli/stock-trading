# AI_GENERATE_START -
from __future__ import annotations

import json
import logging
import os
import re
import socket
import urllib.request
from datetime import date
from decimal import Decimal

from stock_trading.config import AppConfig
from stock_trading.time_utils import shanghai_now

LOGGER = logging.getLogger(__name__)
BARK_PUSH_URL = "https://api.day.app/push"


def push_execution_result(
    config: AppConfig,
    business_date: date,
    result: dict[str, object],
) -> None:
    """推送实时执行闭环最终结果，不把计划价格描述为成交事实。"""
    attempts = list(result["attempts"])
    if not attempts:
        return
    last = attempts[-1]
    side = str(last["side"])
    action = "买入" if side == "BUY" else "卖出"
    status = str(result["status"])
    if status == "FILLED":
        body = "\n".join(
            (
                f"日期：{business_date.isoformat()}",
                f"股票：{last['instrument']}",
                f"最终档位：{last.get('priceLevel', '恢复委托')}",
                f"最后委托价：¥{Decimal(str(last['price'])):,.3f}",
                f"最后委托数量：{int(last['quantity']):,} 股",
                f"尝试次数：{len(attempts)}",
            )
        )
        _push_bark(config, f"[交易] {action}成交完成", body, "交易")
        return
    if status not in {"REJECTED", "UNKNOWN", "STOPPED"}:
        return
    body = "\n".join(
        (
            f"日期：{business_date.isoformat()}",
            f"操作：{action} {last['instrument']}",
            f"状态：{status}",
            f"停止原因：{result['stopReason']}",
            f"原因：{safe_error_text(last.get('errorMessage'))}",
        )
    )
    _push_bark(config, f"[交易] {action}执行未完成", body, "交易异常")


def push_system_failure(
    config: AppConfig | None, command: str, error: Exception
) -> None:
    """尽力推送 CLI 业务异常，通知失败不得影响原业务异常。"""
    body = "\n".join(
        (
            f"任务：{command}",
            f"时间：{shanghai_now().isoformat(timespec='seconds')}",
            f"原因：{safe_error_text(error)}",
        )
    )
    _push_bark(config, "[系统] 任务执行异常", body, "系统异常")


def safe_error_text(value: object) -> str:
    """裁剪并遮蔽异常文本中的常见敏感字段。"""
    text = str(value or "未知原因").replace("\r", " ").replace("\n", " ")
    text = re.sub(
        r"(?i)(token|password|device_key|secret|sign_key)\s*[=:]\s*[^\s,;]+",
        r"\1=***",
        text,
    )
    return text[:500]


def _push_bark(
    config: AppConfig | None,
    title: str,
    body: str,
    group: str,
) -> None:
    device_key = (
        config.bark_device_key
        if config is not None
        else os.getenv("DEVICE_KEY", "").strip()
    )
    if not device_key:
        LOGGER.debug("Bark 推送已跳过：未配置 DEVICE_KEY")
        return
    payload = {
        "device_key": device_key,
        "title": title,
        "group": group,
        "body": f"主机：{socket.gethostname()}\n{body}",
    }
    request = urllib.request.Request(
        BARK_PUSH_URL,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            if not 200 <= response.status < 300:
                raise RuntimeError(f"Bark HTTP 状态异常: {response.status}")
        LOGGER.debug("Bark 推送请求完成：分组=%s；标题=%s", group, title)
    except Exception as error:
        LOGGER.warning("Bark 推送失败，不影响业务：分组=%s；原因=%s", group, error)
# AI_GENERATE_END -
