# AI_GENERATE_START --
from __future__ import annotations

from typing import Annotated

import typer
from rich.console import Console
from rich.markdown import Markdown
from rich.prompt import Prompt
from rich.table import Table

from stock_agent.config import AgentConfig
from stock_agent.memory import MemoryStore
from stock_agent.repair import RepairRuntime
from stock_agent.runtime import StockAgentRuntime


app = typer.Typer(
    add_completion=False,
    invoke_without_command=True,
    no_args_is_help=False,
    rich_markup_mode="rich",
    pretty_exceptions_enable=False,
)
console = Console(highlight=False)


@app.callback(invoke_without_command=True)
def root(
    context: typer.Context,
    new_session: Annotated[
        bool, typer.Option("--new-session", help="创建新会话，不恢复最近会话。")
    ] = False,
    session: Annotated[
        str | None, typer.Option("--session", help="恢复指定会话 ID。")
    ] = None,
) -> None:
    if context.invoked_subcommand is None:
        _interactive(new_session=new_session, session_id=session)


@app.command("ask")
def ask(
    question: Annotated[str, typer.Argument(help="要询问的股票交易问题。")],
    session: Annotated[
        str | None, typer.Option("--session", help="追加到指定会话；默认新建会话。")
    ] = None,
) -> None:
    config, memory = _bootstrap()
    session_id = session or memory.create_session(question[:60] or "单次问答")
    answer = StockAgentRuntime(config, memory).respond(session_id, question)
    console.print(Markdown(answer))


@app.command("repair")
def repair_command(
    run_id: Annotated[int, typer.Argument(help="失败的 GitHub Actions Run ID。")],
    branch: Annotated[
        str | None, typer.Option("--branch", help="目标分支；默认使用运行的 head_branch。")
    ] = None,
    dry_run: Annotated[
        bool, typer.Option("--dry-run", help="只分析并生成修改方案，不提交 GitHub。")
    ] = False,
) -> None:
    config, memory = _bootstrap(require_github=True)
    _execute_repair(config, memory, run_id, branch=branch, dry_run=dry_run)


@app.command("repairs")
def repairs_command(
    limit: Annotated[int, typer.Option("--limit", min=1, max=100)] = 20,
) -> None:
    _, memory = _bootstrap(require_model=False)
    table = Table("任务 ID", "Run ID", "分支", "状态", "最后更新")
    for row in memory.list_repair_tasks(limit):
        table.add_row(
            str(row["task_id"]), str(row["run_id"]),
            str(row["target_branch"]), str(row["status"]),
            str(row["updated_at"]),
        )
    console.print(table)


@app.command("history")
def history() -> None:
    _, memory = _bootstrap(require_model=False)
    _print_sessions(memory)


@app.command("memory")
def memory_command(
    query: Annotated[
        str | None, typer.Option("--query", help="搜索历史消息和长期记忆。")
    ] = None,
) -> None:
    _, memory = _bootstrap(require_model=False)
    rows = memory.search(query) if query else memory.list_memories()
    table = Table("来源", "分类", "内容")
    for row in rows:
        table.add_row(
            str(row.get("source", row.get("id", "memory"))),
            str(row.get("role", row.get("category", ""))),
            str(row.get("content", ""))[:500],
        )
    console.print(table)


@app.command("status")
def status() -> None:
    config, _ = _bootstrap(require_model=False)
    table = Table("项目", "状态")
    table.add_row("模型", config.deepseek_model)
    table.add_row("DeepSeek API Key", "已配置" if config.deepseek_api_key else "未配置")
    table.add_row("GitHub", config.github_repository)
    table.add_row("GitHub Token", "已配置" if config.github_token else "未配置")
    table.add_row("修复目标分支", config.github_target_branch)
    table.add_row("交易数据", str(config.trading_data_dir))
    table.add_row("交易 SQLite", "存在" if config.state_db_path.exists() else "不存在")
    table.add_row("Agent 记忆", str(config.memory_db_path))
    table.add_row("能力", "只读业务问答、GitHub 日志诊断、两轮代码修复")
    console.print(table)


def _interactive(new_session: bool, session_id: str | None) -> None:
    config, memory = _bootstrap()
    active_session = session_id
    if active_session is None and not new_session:
        active_session = memory.latest_session()
    if active_session is None:
        active_session = memory.create_session()
    runtime = StockAgentRuntime(config, memory)
    console.print("[bold]Stock Agent[/bold]")
    console.print(f"模型: {config.deepseek_model}")
    console.print(f"数据: {config.trading_data_dir}")
    console.print(f"会话: {active_session}")
    console.print("输入 /new、/history、/memory [关键词]、/repair <run-id> 或 /exit。")
    while True:
        try:
            text = Prompt.ask("\n[bold cyan]你[/bold cyan]").strip()
        except (EOFError, KeyboardInterrupt):
            console.print("\n已退出。")
            return
        if not text:
            continue
        if text in {"/exit", "/quit"}:
            return
        if text == "/new":
            active_session = memory.create_session()
            console.print(f"已创建会话: {active_session}")
            continue
        if text == "/history":
            _print_sessions(memory)
            continue
        if text == "/repairs":
            for row in memory.list_repair_tasks():
                console.print_json(data=row)
            continue
        if text.startswith("/memory"):
            query = text[len("/memory"):].strip()
            rows = memory.search(query) if query else memory.list_memories()
            console.print_json(data=rows)
            continue
        if text.startswith("/repair"):
            value = text[len("/repair"):].strip()
            if not value.isdigit():
                console.print("[red]用法：/repair <GitHub Actions Run ID>[/red]")
                continue
            if not config.github_token:
                console.print("[red]未配置 STOCK_AGENT_GITHUB_TOKEN。[/red]")
                continue
            _execute_repair(config, memory, int(value), branch=None, dry_run=False)
            continue
        try:
            answer = runtime.respond(active_session, text)
        except Exception as error:
            console.print(f"[red]请求失败：{error}[/red]")
            continue
        console.print("\n[bold green]Agent[/bold green]")
        console.print(Markdown(answer))


def _execute_repair(
    config: AgentConfig,
    memory: MemoryStore,
    run_id: int,
    *,
    branch: str | None,
    dry_run: bool,
) -> None:
    runtime = RepairRuntime(config, memory)
    try:
        result = runtime.repair(run_id, branch=branch, dry_run=dry_run)
    except Exception as error:
        console.print(f"[red]修复失败：{error}[/red]")
        raise typer.Exit(1) from error
    finally:
        runtime.close()
    console.print_json(data=result)


def _print_sessions(memory: MemoryStore) -> None:
    table = Table("会话 ID", "标题", "最后更新")
    for row in memory.list_sessions():
        table.add_row(row["session_id"], row["title"], row["updated_at"])
    console.print(table)


def _bootstrap(
    require_model: bool = True,
    require_github: bool = False,
) -> tuple[AgentConfig, MemoryStore]:
    config = AgentConfig.from_env()
    config.ensure_runtime_dirs()
    if require_model and not config.deepseek_api_key:
        raise typer.BadParameter(
            "未配置 DEEPSEEK_API_KEY；请写入 ~/stock-agent/.env 或系统环境变量。"
        )
    if require_github and not config.github_token:
        raise typer.BadParameter("未配置 STOCK_AGENT_GITHUB_TOKEN。")
    return config, MemoryStore(config.memory_db_path)


def main() -> None:
    app()
# AI_GENERATE_END --