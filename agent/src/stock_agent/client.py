# AI_GENERATE_START ---
from __future__ import annotations

from typing import Any

from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AuthenticationError,
    OpenAI,
    RateLimitError,
)

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
        *,
        max_tokens: int = 4096,
        temperature: float = 0.1,
    ) -> Any:
        try:
            return self._client.chat.completions.create(
                model=self._model,
                messages=messages,
                tools=tools,
                tool_choice="auto",
                temperature=temperature,
                max_tokens=max_tokens,
            )
        except AuthenticationError as error:
            raise RuntimeError("DeepSeek 认证失败，请检查 API Key") from error
        except RateLimitError as error:
            raise RuntimeError("DeepSeek 请求达到限流，请稍后重试") from error
        except APITimeoutError as error:
            raise RuntimeError("DeepSeek 请求超时") from error
        except APIConnectionError as error:
            raise RuntimeError("DeepSeek 网络连接失败") from error
        except APIStatusError as error:
            raise RuntimeError(f"DeepSeek API 请求失败: HTTP {error.status_code}") from error
# AI_GENERATE_END ---