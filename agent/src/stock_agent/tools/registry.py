# AI_GENERATE_START -
from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ToolDefinition:
    name: str
    description: str
    parameters: dict[str, Any]
    handler: Callable[[dict[str, Any]], dict[str, Any]]

    def api_definition(self) -> dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }


class ToolRegistry:
    """Allowlisted read-only tools exposed to the model."""

    def __init__(self) -> None:
        self._tools: dict[str, ToolDefinition] = {}

    def register(self, definition: ToolDefinition) -> None:
        if definition.name in self._tools:
            raise ValueError(f"工具重复注册: {definition.name}")
        self._tools[definition.name] = definition

    def definitions(self) -> list[dict[str, Any]]:
        return [tool.api_definition() for tool in self._tools.values()]

    def execute(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        tool = self._tools.get(name)
        if tool is None:
            return {"status": "error", "error": f"未知工具: {name}"}
        try:
            return tool.handler(arguments)
        except Exception as error:
            return {
                "status": "error",
                "error": str(error) or error.__class__.__name__,
            }
# AI_GENERATE_END -
