# AI_GENERATE_START --------
from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable

from rich.console import Console
from rich.table import Table

from stock_trading.config import AppConfig
from stock_trading.data_sync import compare_sync_data
from stock_trading.market.reporting import COLLECTION_TYPES, read_market_summary
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.model_config import MODEL_SPECS, ModelSpec
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.state.store import StateStore
from stock_trading.utils.time_utils import count_holding_trade_days


@dataclass(frozen=True)
class ReportTable:
    title: str
    columns: tuple[str, ...]
    rows: tuple[tuple[str, ...], ...]
    note: str | None = None


def render_report(tables: Iterable[ReportTable]) -> None:
    console = Console()
    for index, report in enumerate(tables):
        if index:
            console.print()
        table = Table(title=report.title, header_style="bold cyan")
        for column in report.columns:
            table.add_column(column, overflow="fold")
        if report.rows:
            for row in report.rows:
                table.add_row(*row)
        else:
            table.add_row("暂无数据", *("" for _ in report.columns[1:]))
        console.print(table)
        if report.note:
            console.print(f"[dim]{report.note}[/dim]")


def collect_report(
    config: AppConfig, selected_types: tuple[str, ...] | None = None
) -> tuple[ReportTable, ...]:
    types = selected_types or COLLECTION_TYPES
    rows = []
    missing_summary = False
    for data_type in types:
        summary = read_market_summary(config.data_dir, data_type)
        missing_summary = missing_summary or summary is None
        summary = summary or {}
        rows.append(
            (
                data_type,
                _integer(summary.get("codes")),
                _integer(summary.get("files")),
                _integer(summary.get("rows")),
                _date_range(summary.get("startDate"), summary.get("endDate")),
                format_bytes(summary.get("bytes")),
                _integer(summary.get("validCodes")),
                format_percent(summary.get("coverage")),
                _display_time(summary.get("updatedAt")),
            )
        )
    return (
        ReportTable(
            "行情采集数据汇报",
            (
                "类型",
                "代码数",
                "文件数",
                "总行数",
                "日期范围",
                "占用空间",
                "有效代码",
                "最新覆盖率",
                "摘要时间",
            ),
            tuple(rows),
            (
                "部分数据类型尚无成功采集摘要，相关列显示为 -；"
                "下次普通 stock-collect 成功后会自动生成。"
                if missing_summary
                else "摘要由最近一次质量检查通过的普通采集原子更新。"
            ),
        ),
    )


def prepare_report(
    config: AppConfig, spec: ModelSpec | None = None
) -> tuple[ReportTable, ...]:
    rows = []
    missing_summary = False
    for current in _selected_specs(spec):
        target = current.qlib_dir(config.qlib_dir)
        if not target.is_dir():
            continue
        summary = QlibBinaryStore(
            ParquetMarketStore(config.data_dir), target, current
        ).report_summary()
        if summary is None:
            continue
        missing_summary = missing_summary or not bool(summary.get("summaryAvailable"))
        rows.append(
            (
                current.model_id,
                current.feature_frequency,
                _date_range(summary.get("startDate"), summary.get("endDate")),
                _integer(summary.get("tradingDays")),
                _integer(summary.get("instruments")),
                _integer(summary.get("validFeatureInstruments")),
                format_percent(summary.get("coverage")),
                "有效" if summary.get("contractValid") else "不匹配",
                format_bytes(summary.get("bytes")),
                _display_time(summary.get("updatedAt")),
            )
        )
    return (
        ReportTable(
            "模型特征数据准备汇报",
            (
                "模型",
                "特征频率",
                "日期范围",
                "交易日",
                "股票数",
                "有效特征股票",
                "覆盖率",
                "契约",
                "空间",
                "摘要时间",
            ),
            tuple(rows),
            (
                "部分旧 Qlib 目录尚无摘要，完整性、覆盖率和空间显示为 -；"
                "下次普通 stock-prepare 成功后会自动生成。"
                if missing_summary
                else None
            ),
        ),
    )


def model_report(
    config: AppConfig, spec: ModelSpec | None = None
) -> tuple[ReportTable, ...]:
    rows: list[tuple[str, ...]] = []
    for current in _selected_specs(spec):
        root = current.artifact_dir(config.artifact_dir)
        metrics = _read_json(root / "metrics.json")
        evaluation = _read_json(root / "evaluation.json")
        if metrics is None and evaluation is None:
            continue
        metric_values = dict((metrics or {}).get("metrics", {}))
        segments = dict((metrics or {}).get("segments", {}))
        model_values = (
            current.model_id,
            current.algorithm,
            current.feature_set,
            _date_range(segments.get("train_start"), segments.get("train_end")),
            "/".join(
                (
                    _integer((metrics or {}).get("trainRows")),
                    _integer((metrics or {}).get("validRows")),
                    _integer((metrics or {}).get("rows")),
                )
            ),
            _integer((metrics or {}).get("featureCount")),
            "/".join(
                (
                    format_number(metric_values.get("icMean"), 4),
                    format_number(metric_values.get("rankIcMean"), 4),
                )
            ),
            f"{current.label_name}/{current.training_target}",
            ", ".join(
                f"{key}={value}"
                for key, value in current.algorithm_parameters.items()
            ),
        )
        scenarios = dict((evaluation or {}).get("backtests", {}))
        scenario_lines = []
        for capital, value in scenarios.items():
            scenario = dict(value)
            scenario_lines.append(
                " | ".join(
                    (
                        format_amount(capital),
                        format_amount(scenario.get("grossProfit")),
                        format_percent(scenario.get("grossReturn")),
                        format_percent(scenario.get("maxDrawdown")),
                        _integer(scenario.get("closedTrades")),
                        format_number(scenario.get("averageHoldingDays"), 2),
                    )
                )
            )
        rows.append(model_values + ("\n".join(scenario_lines) or "-",))
    return (
        ReportTable(
            "模型训练与回测汇报（毛收益口径）",
            (
                "模型",
                "算法",
                "特征",
                "训练区间",
                "样本(训/验/测)",
                "特征数",
                "IC/RankIC",
                "标签/训练目标",
                "算法参数",
                "资金场景",
            ),
            tuple(rows),
            "资金场景依次为：资金 | 毛利润 | 毛收益率 | 毛回撤 | 闭环数 | 平均持仓日。"
            "模型评价不扣固定买卖费用。",
        ),
    )


def trading_report(
    config: AppConfig, limit: int, direction: str | None = None
) -> tuple[ReportTable, ...]:
    store = StateStore(config.state_db_path)
    rows = []
    for item in store.filled_order_report(direction, limit):
        price = item.get("average_fill_price")
        quantity = item.get("filled_quantity")
        rows.append(
            (
                str(item.get("instrument") or "-"),
                str(item.get("stock_name") or "-"),
                {"BUY": "买入", "SELL": "卖出"}.get(
                    str(item.get("direction")),
                    str(item.get("direction") or "-"),
                ),
                format_amount(item.get("order_price")),
                _integer(item.get("order_quantity")),
                format_amount(price),
                format_amount(float(price or 0) * int(quantity or 0)),
                _display_time(item.get("order_time")),
            )
        )
    title = (
        f"最近{limit}条{'买入' if direction == 'BUY' else '卖出'}成交订单"
        if direction
        else f"最近{limit}条历史订单"
    )
    return (
        ReportTable(
            title,
            (
                "股票代码",
                "股票名称",
                "方向",
                "委托价",
                "委托数量",
                "成交均价",
                "成交金额",
                "委托时间",
            ),
            tuple(rows),
        ),
    )

def order_report(
    config: AppConfig,
    limit: int,
    start_date: date | None = None,
    end_date: date | None = None,
) -> tuple[ReportTable, ...]:
    store = StateStore(config.state_db_path)
    closed = []
    for item in store.realized_trade_report(limit, start_date, end_date):
        calendar = _report_calendar(config, item.get("model_id"))
        holding_days = count_holding_trade_days(
            date.fromisoformat(str(item["buy_date"])),
            date.fromisoformat(str(item["sell_date"])),
            calendar,
        )
        closed.append(
            (
                str(item["instrument"]),
                str(item.get("stock_name") or "-"),
                str(item["buy_date"]),
                str(item["sell_date"]),
                str(holding_days),
                _integer(item["quantity"]),
                format_amount(item["buy_price"]),
                format_amount(item["sell_price"]),
                format_amount(float(item["buy_fee"]) + float(item["sell_fee"])),
                format_amount(item["gross_profit"]),
                format_percent(item["gross_return"]),
                format_amount(item["net_profit"]),
                format_percent(item["net_return"]),
                str(item.get("model_id") or "-"),
            )
        )
    return (
        ReportTable(
            f"最近{limit}条已闭环交易",
            ("股票代码", "股票名称", "买入日", "卖出日", "持仓交易日", "数量", "买入价", "卖出价", "真实费用", "毛收益", "毛收益率", "净收益", "净收益率", "模型"),
            tuple(closed),
        ),
    )


def review_report(
    config: AppConfig, spec: ModelSpec | None = None
) -> tuple[ReportTable, ...]:
    model_id = spec.model_id if spec else None
    rows = []
    configured = {item.model_id for item in MODEL_SPECS}
    for item in StateStore(config.state_db_path).latest_model_evaluation_report(model_id):
        if item["model_id"] not in configured:
            continue
        rows.append(
            (
                str(item["model_id"]),
                str(item["business_date"]),
                f"{item['observations']}/{item['window_observations']}",
                format_percent(item.get("positive_period_ratio")),
                format_percent(item.get("total_gross_return")),
                format_percent(item.get("total_net_return")),
                format_percent(item.get("max_drawdown")),
                str(item["decision"]),
                str(item.get("training_status") or "-"),
                str(item["reason"]),
            )
        )
    return (
        ReportTable(
            "模型滚动评估汇报",
            ("模型", "评估日期", "样本", "正收益比例", "毛收益率", "净收益率", "回撤", "结论", "重训状态", "原因"),
            tuple(rows),
        ),
    )


def clean_report(config: AppConfig, limit: int) -> tuple[ReportTable, ...]:
    compatible, values = StateStore(config.state_db_path).cleanup_report(limit)
    rows = []
    for item in values:
        result = _parse_json(item.get("result_json"))
        market = dict(result.get("market", {}))
        state = dict(result.get("state", {}))
        storage = dict(result.get("storage", {}))
        rows.append(
            (
                str(item["business_date"]),
                str(item["status"]),
                _integer(market.get("beforeRawRows")),
                _integer(market.get("rawRows")),
                _integer(market.get("deletedRawRows")),
                _integer(state.get("deletedRows")),
                format_bytes(storage.get("beforeBytes")),
                format_bytes(storage.get("afterBytes")),
                format_bytes(storage.get("releasedBytes")),
            )
        )
    if not compatible:
        note = "当前数据库尚无 result_json 字段，请先执行一次普通 stock-clean 完成无损升级。"
    elif any(not item.get("result_json") for item in values):
        note = "升级前的历史清理记录没有结构化明细，相关列显示为 -。"
    else:
        note = None
    return (
        ReportTable(
            f"最近{limit}次数据清理汇报",
            ("基准日期", "状态", "清理前分钟行", "清理后分钟行", "删除分钟行", "SQLite删除", "清理前空间", "清理后空间", "释放空间"),
            tuple(rows),
            note,
        ),
    )


def sync_report(config: AppConfig) -> tuple[ReportTable, ...]:
    rows = tuple(
        (
            str(item["scope"]),
            str(item["localFiles"]),
            format_bytes(item["localBytes"]),
            str(item["remoteFiles"]),
            format_bytes(item["remoteBytes"]),
            str(item["sameFiles"]),
            str(item["pullableFiles"]),
            str(item["pushableFiles"]),
            str(item["conflictFiles"]),
        )
        for item in compare_sync_data(config, _sync_progress)
    )
    return (
        ReportTable(
            "本地与服务器数据差异汇报",
            ("范围", "本地文件", "本地大小", "远端文件", "远端大小", "一致", "可拉取", "可推送", "冲突"),
            rows,
            "按相对路径和文件大小比较，不是内容哈希校验；本命令只执行 SSH 只读清单查询。",
        ),
    )


def format_amount(value: object) -> str:
    if value is None or value == "":
        return "-"
    try:
        return f"{float(value):,.2f}"
    except (TypeError, ValueError):
        return str(value)


def format_number(value: object, digits: int = 2) -> str:
    if value is None:
        return "-"
    return f"{float(value):.{digits}f}"


def format_percent(value: object) -> str:
    if value is None:
        return "-"
    return f"{float(value) * 100:.2f}%"


def format_bytes(value: object) -> str:
    if value is None:
        return "-"
    size = float(value)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if size < 1024 or unit == "TB":
            return f"{size:.0f} {unit}" if unit == "B" else f"{size:.2f} {unit}"
        size /= 1024
    return "-"


def mask_identifier(value: object) -> str:
    text = str(value or "")
    if not text:
        return "-"
    return f"****{text[-4:]}" if len(text) > 4 else "****"


def _selected_specs(spec: ModelSpec | None) -> tuple[ModelSpec, ...]:
    return (spec,) if spec else MODEL_SPECS


def _read_json(path: Path) -> dict[str, object] | None:
    if not path.is_file():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return value if isinstance(value, dict) else None


def _parse_json(value: object) -> dict[str, object]:
    if not value:
        return {}
    try:
        result = json.loads(str(value))
    except ValueError:
        return {}
    return result if isinstance(result, dict) else {}


def _report_calendar(config: AppConfig, model_id: object = None) -> Path | None:
    market_calendar = config.data_dir / "calendar" / "trade_dates.txt"
    if market_calendar.is_file():
        return market_calendar
    specs = sorted(
        MODEL_SPECS,
        key=lambda spec: spec.model_id != str(model_id or ""),
    )
    for spec in specs:
        path = spec.qlib_dir(config.qlib_dir) / "calendars" / "day.txt"
        if path.is_file():
            return path
    legacy = config.qlib_dir / "calendars" / "day.txt"
    return legacy if legacy.is_file() else None


def _date_range(start: object, end: object) -> str:
    if not start and not end:
        return "-"
    if start == end or not end:
        return str(start or end)
    return f"{start} 至 {end}"


def _integer(value: object) -> str:
    return "-" if value is None else f"{int(value):,}"


def _display_time(value: object) -> str:
    if not value:
        return "-"
    text = str(value)
    return text.replace("T", " ")[:19]


def _sync_progress(message: str) -> None:
    Console().print(f"[dim]{message}[/dim]")
# AI_GENERATE_END --------
