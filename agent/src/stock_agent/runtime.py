# AI_GENERATE_START -
from __future__ import annotations

import json
from typing import Any

from stock_agent.client import DeepSeekClient
from stock_agent.config import AgentConfig
from stock_agent.memory import MemoryStore
from stock_agent.tools import ParquetTools, StateTools, ToolRegistry


SYSTEM_PROMPT = """
你是只服务于一个用户的 A 股 T+1 交易数据 Agent。

当前阶段只允许读取本地 SQLite、Parquet、Qlib/模型制品和 Agent 记忆，不允许修改交易数据、
提交订单、修改代码、调用 GitHub 写接口或执行系统命令。

业务口径：
1. 真实收益只来自 realized_trade 中已经完成买入和卖出匹配的闭环。
2. 未卖出持仓、账户权益变化、入金和出金不属于已实现策略收益。
3. 毛收益与真实费用后的净收益必须分别说明，不能混用。
4. 数据问题必须先调用工具获取事实，不能凭常识虚构数字。
5. 回答必须说明数据来源和查询范围；工具失败或数据不足时明确说明边界。
6. query_state_sql 和 query_parquet 仅在固定业务工具不能回答时使用。
7. 只有用户明确说“记住”时才能调用 remember_user_fact。
8. 涉及历史偏好或以前结论时，可调用 search_agent_memory。

回答使用中文，证券代码、字段名、命令和协议字段保留英文。输出简洁、可核对。
""".strip()


class StockAgentRuntime:
    """Conversation loop with a small deterministic read-only tool surface."""

    def __init__(self, config: AgentConfig, memory: MemoryStore) -> None:
        self._config = config
        self._memory = memory
        self._client = DeepSeekClient(config)
        self._registry = ToolRegistry()
        StateTools(config, memory).register(self._registry)
        ParquetTools(config).register(self._registry)

    def respond(self, session_id: str, user_text: str) -> str:
        self._memory.append_message(session_id, "user", user_text)
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            *self._memory.load_messages(session_id),
        ]
        for _ in range(self._config.max_tool_rounds):
            response = self._client.complete(messages, self._registry.definitions())
            message = response.choices[0].message
            messages.append(message.model_dump(exclude_none=True))
            if not message.tool_calls:
                content = (message.content or "").strip() or "模型未返回有效回答。"
                self._memory.append_message(session_id, "assistant", content)
                return content
            for call in message.tool_calls:
                arguments = _parse_arguments(call.function.arguments)
                result = self._registry.execute(call.function.name, arguments)
                result_text = json.dumps(result, ensure_ascii=False, default=str)
                self._memory.audit_tool(
                    session_id=session_id,
                    tool_name=call.function.name,
                    arguments_summary=json.dumps(arguments, ensure_ascii=False, default=str),
                    result_status=str(result.get("status", "unknown")),
                    result_summary=result_text,
                )
                messages.append({
                    "role": "tool", "tool_call_id": call.id, "content": result_text,
                })
        content = "工具调用轮数达到上限，已停止本次回答；请缩小查询范围后重试。"
        self._memory.append_message(session_id, "assistant", content)
        return content


def _parse_arguments(value: str | None) -> dict[str, Any]:
    if value is None or not value.strip():
        return {}
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as error:
        raise ValueError("模型返回的工具参数不是有效 JSON") from error
    if not isinstance(parsed, dict):
        raise ValueError("模型返回的工具参数必须是 JSON 对象")
    return parsed
# AI_GENERATE_END -
