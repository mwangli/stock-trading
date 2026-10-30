# AI_GENERATE_START -
from __future__ import annotations

import base64
import json
import re
import time
from io import BytesIO
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from PIL import Image

from stock_trading.config import AppConfig

BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
BAIDU_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic"
EXPRESSION_PATTERN = re.compile(r"(\d{1,2})([+\-*/])(\d{1,2})")


class BaiduCaptchaSolver:
    """Recognize arithmetic broker captchas through two matching OCR paths."""

    def __init__(self, config: AppConfig) -> None:
        self._config = config
        self._access_token: str | None = None
        self._access_token_expire_at = 0.0
        self._last_request_at = 0.0

    def solve(self, image_bytes: bytes) -> int:
        if not self._config.broker_captcha_auto_submit_enabled:
            raise RuntimeError("验证码 OCR 自动提交未启用")
        if not image_bytes:
            raise RuntimeError("验证码图片为空")
        enlarged = self._enlarge_png(image_bytes)
        original_expression = self._recognize(image_bytes)
        enlarged_expression = self._recognize(enlarged)
        if not original_expression or original_expression != enlarged_expression:
            raise RuntimeError("验证码 OCR 双路径结果不一致")
        return self._evaluate(original_expression)

    def _recognize(self, image_bytes: bytes) -> str | None:
        access_token = self._get_access_token()
        self._throttle()
        response = self._post_form(
            f"{BAIDU_OCR_URL}?access_token={access_token}",
            {
                "image": base64.b64encode(image_bytes).decode("ascii"),
                "detect_direction": "false",
                "probability": "true",
            },
        )
        if response.get("error_code") is not None:
            return None
        words = response.get("words_result")
        if not isinstance(words, list) or not words:
            return None
        text = "".join(
            str(item.get("words", "")) for item in words if isinstance(item, dict)
        )
        return self._normalize(text)

    def _get_access_token(self) -> str:
        if not self._config.broker_baidu_ocr_api_key or not self._config.broker_baidu_ocr_secret_key:
            raise RuntimeError("未配置百度 OCR 凭据")
        now = time.time()
        if self._access_token and now < self._access_token_expire_at:
            return self._access_token
        response = self._post_form(
            BAIDU_TOKEN_URL,
            {
                "grant_type": "client_credentials",
                "client_id": self._config.broker_baidu_ocr_api_key,
                "client_secret": self._config.broker_baidu_ocr_secret_key,
            },
        )
        token = response.get("access_token")
        if not token:
            raise RuntimeError("百度 OCR AccessToken 获取失败")
        expires_in = max(120, int(response.get("expires_in", 2592000)))
        self._access_token = str(token)
        self._access_token_expire_at = now + expires_in - 60
        return self._access_token

    def _post_form(self, url: str, values: dict[str, str]) -> dict[str, object]:
        request = Request(
            url,
            data=urlencode(values).encode("utf-8"),
            method="POST",
        )
        request.add_header("Content-Type", "application/x-www-form-urlencoded")
        with urlopen(request, timeout=self._config.broker_ocr_timeout_seconds) as response:
            payload = json.loads(response.read().decode("utf-8"))
        if not isinstance(payload, dict):
            raise RuntimeError("OCR 响应不是 JSON 对象")
        return payload

    def _throttle(self) -> None:
        wait_seconds = (
            self._last_request_at
            + self._config.broker_ocr_request_interval_seconds
            - time.monotonic()
        )
        if wait_seconds > 0:
            time.sleep(wait_seconds)
        self._last_request_at = time.monotonic()

    @staticmethod
    def _enlarge_png(image_bytes: bytes) -> bytes:
        try:
            with Image.open(BytesIO(image_bytes)) as image:
                source = image.convert("RGB")
                scaled = source.resize(
                    (source.width * 6, source.height * 6), Image.Resampling.BICUBIC
                )
                output = BytesIO()
                scaled.save(output, format="PNG")
                return output.getvalue()
        except Exception as error:
            raise RuntimeError("验证码图片读取或放大失败") from error

    @staticmethod
    def _normalize(text: str) -> str | None:
        value = re.sub(r"\s+", "", text or "")
        value = (
            value.replace("＋", "+")
            .replace("－", "-")
            .replace("—", "-")
            .replace("×", "*")
            .replace("x", "*")
            .replace("X", "*")
            .replace("÷", "/")
        )
        match = EXPRESSION_PATTERN.search(value)
        return "".join(match.groups()) if match else None

    @staticmethod
    def _evaluate(expression: str) -> int:
        match = EXPRESSION_PATTERN.fullmatch(expression or "")
        if match is None:
            raise RuntimeError("OCR 返回的表达式格式非法")
        left, operator, right = int(match.group(1)), match.group(2), int(match.group(3))
        if operator == "+":
            return left + right
        if operator == "-":
            return left - right
        if operator == "*":
            return left * right
        if right == 0 or left % right != 0:
            raise RuntimeError("OCR 返回了非法除法表达式")
        return left // right
# AI_GENERATE_END -
