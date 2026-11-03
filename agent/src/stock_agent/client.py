# AI_GENERATE_START -
from __future__ import annotations

from typing import Any

from openai import OpenAI

from stock_agent.config import AgentConfig


class DeepSeekClient:
    """Small OpenAI-compatible client dedicated to DeepSeek chat calls."""

    def __init__(self, config: AgentConfig) -> None:
        if not config.deepseek_api_key:
            raise ValueError(
                "未配置 DEEPSEEK_API_KEY；请写入 ~/stock-agent/.env 或系统环境变量。"
            )
        self._model = config.deepseek_model
        self._client = OpenAI(
            api_key=config.deepseek_api_key,
            base_url=config.deepseek_base_url,
            timeout=config.request_timeout_seconds,
            max_retries=1,
        )

    def complete(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
    ) -> Any:
        return self._client.chat.completions.create(
            model=self._model,
            messages=messages,
            tools=tools,
            tool_choice="auto",
            temperature=0.1,
            max_tokens=4096,
        )
# AI_GENERATE_END -
