# AI_GENERATE_START -----------------------
from __future__ import annotations

import json
import shutil
import unicodedata
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable

from stock_trading.config import AppConfig
from stock_trading.market.reporting import COLLECTION_TYPES, read_market_summary
from stock_trading.research.model_config import MODEL_SPECS, ModelSpec
from stock_trading.research.qlib_contract import PREPARE_SUMMARY_FILE
from stock_trading.state.store import StateStore
from stock_trading.utils.console import BOLD_CYAN, DIM, echo, style
from stock_trading.utils.time_utils import count_holding_trade_days


@dataclass(frozen=True)
class ReportTable:
    title: str
    columns: tuple[str, ...]
    rows: tuple[tuple[str, ...], ...]
    note: str | None = None


def render_report(tables: Iterable[ReportTable]) -> None:
    for index, report in enumerate(tables):
        if index:
            echo()
        widths = _fit_column_widths(
            report, shutil.get_terminal_size((100, 24)).columns
        )
        if widths is not None:
            _render_horizontal_report(report, widths)
        else:
            _render_vertical_report(report)
        if report.note:
            echo(report.note, color=DIM)


def _fit_column_widths(
    report: ReportTable, terminal_width: int
) -> tuple[int, ...] | None:
    natural = _column_widths(report)
    border_width = len(report.columns) * 3 + 1
    if sum(natural) + border_width <= terminal_width:
        return natural
    if terminal_width < 100:
        return None
    available = terminal_width - border_width
    widths = [
        max(_display_width(column), min(width, 8))
        for column, width in zip(report.columns, natural)
    ]
    if sum(widths) > available:
        return None
    remaining = available - sum(widths)
    while remaining:
        index = max(
            range(len(widths)),
            key=lambda current: natural[current] - widths[current],
        )
        if widths[index] >= natural[index]:
            break
        widths[index] += 1
        remaining -= 1
    return tuple(widths)


def _column_widths(report: ReportTable) -> tuple[int, ...]:
    rows = report.rows or (("暂无数据", *("" for _ in report.columns[1:])),)
    return tuple(
        max(
            _display_width(line)
            for value in (report.columns[index], *(row[index] for row in rows))
            for line in str(value).splitlines() or ("",)
        )
        for index in range(len(report.columns))
    )


def _render_horizontal_report(
    report: ReportTable, widths: tuple[int, ...]
) -> None:
    border = "+" + "+".join("-" * (width + 2) for width in widths) + "+"
    echo(report.title, color=BOLD_CYAN)
    echo(border)
    _render_horizontal_row(report.columns, widths, header=True)
    echo(border)
    rows = report.rows or (("暂无数据", *("" for _ in report.columns[1:])),)
    for row in rows:
        _render_horizontal_row(row, widths)
        echo(border)


def _render_horizontal_row(
    values: tuple[str, ...],
    widths: tuple[int, ...],
    *,
    header: bool = False,
) -> None:
    lines = [_wrap_display(value, width) for value, width in zip(values, widths)]
    for line_index in range(max(len(value) for value in lines)):
        cells = []
        for value, width in zip(lines, widths):
            text = value[line_index] if line_index < len(value) else ""
            padded = _pad(text, width)
            cells.append(style(padded, BOLD_CYAN) if header else padded)
        echo("|" + "|".join(f" {cell} " for cell in cells) + "|")


def _wrap_display(value: object, width: int) -> list[str]:
    wrapped: list[str] = []
    for source in str(value).splitlines() or [""]:
        line = ""
        line_width = 0
        for character in source:
            character_width = _display_width(character)
            if line and line_width + character_width > width:
                wrapped.append(line)
                line = ""
                line_width = 0
            line += character
            line_width += character_width
        wrapped.append(line)
    return wrapped or [""]


def _render_vertical_report(report: ReportTable) -> None:
    echo(report.title, color=BOLD_CYAN)
    if not report.rows:
        echo("暂无数据", color=DIM)
        return
    label_width = max(_display_width(column) for column in report.columns)
    for index, row in enumerate(report.rows, start=1):
        echo(f"----- [{index}] -----", color=DIM)
        for column, value in zip(report.columns, row):
            lines = str(value or "-").splitlines() or ["-"]
            label = style(_pad(column, label_width), BOLD_CYAN)
            echo(f"{label} : {lines[0]}")
            for line in lines[1:]:
                echo(f"{' ' * label_width}   {line}")


def _pad(value: object, width: int) -> str:
    text = str(value)
    return text + " " * max(width - _display_width(text), 0)


def _display_width(value: str) -> int:
    return sum(
        0 if unicodedata.combining(character) else
        2 if unicodedata.east_asian_width(character) in {"W", "F"} else 1
        for character in value
    )


def summary_report(
    config: AppConfig,
    spec: ModelSpec | None = None,
    start_date: date | None = None,
    end_date: date | None = None,
) -> tuple[ReportTable, ...]:
    """返回统一的本地只读汇报。"""
    return (
        collect_report(config),
        model_report(config, spec),
        order_report(config, config.report_limit, start_date, end_date),
    )


def collect_report(config: AppConfig) -> ReportTable:
    rows = []
    missing_summary = False
    for data_type in COLLECTION_TYPES:
        summary = read_market_summary(config.data_dir, data_type)
        missing_summary = missing_summary or summary is None
        summary = summary or {}
        rows.append(
            (
                data_type,
                _integer(summary.get("codes")),
                _integer(summary.get("rows")),
                _date_range(summary.get("startDate"), summary.get("endDate")),
                _integer(summary.get("validCodes")),
                format_percent(summary.get("coverage")),
                format_bytes(summary.get("bytes")),
                _display_time(summary.get("updatedAt")),
            )
        )
    return ReportTable(
        "行情数据状态",
        (
            "类型",
            "代码数",
            "总行数",
            "日期范围",
            "有效代码",
            "覆盖率",
            "空间",
            "更新时间",
        ),
        tuple(rows),
        (
            "部分数据尚无采集摘要；下次 stock-trading collect 成功后自动生成。"
            if missing_summary
            else None
        ),
    )


def model_report(
    config: AppConfig, spec: ModelSpec | None = None
) -> ReportTable:
    rows: list[tuple[str, ...]] = []
    evaluations = {
        str(item["model_id"]): item
        for item in StateStore(config.state_db_path).latest_model_evaluation_report(
            spec.model_id if spec else None
        )
    }
    for current in _selected_specs(spec):
        root = current.artifact_dir(config.artifact_dir)
        prepared = _read_json(
            current.qlib_dir(config.qlib_dir) / PREPARE_SUMMARY_FILE
        )
        metrics = _read_json(root / "metrics.json")
        evaluation = _read_json(root / "evaluation.json")
        latest = evaluations.get(current.model_id)
        if prepared is None and metrics is None and evaluation is None and latest is None:
            continue
        metric_values = dict((metrics or {}).get("metrics", {}))
        backtest = dict((evaluation or {}).get("backtest", {}))
        if not backtest:
            backtests = dict((evaluation or {}).get("backtests", {}))
            backtest = dict(backtests.get(str(config.backtest_capital), {}))
        review = "-"
        if latest is not None:
            review = f"{latest['business_date']} {latest['decision']}"
            if latest.get("training_status"):
                review += f"/{latest['training_status']}"
        rows.append(
            (
                current.model_id,
                current.algorithm,
                _date_range(
                    (prepared or {}).get("startDate"),
                    (prepared or {}).get("endDate"),
                ),
                format_percent((prepared or {}).get("coverage")),
                "/".join(
                    (
                        _integer((metrics or {}).get("trainRows")),
                        _integer((metrics or {}).get("validRows")),
                        _integer((metrics or {}).get("rows")),
                    )
                ),
                "/".join(
                    (
                        format_number(metric_values.get("icMean"), 4),
                        format_number(metric_values.get("rankIcMean"), 4),
                    )
                ),
                format_amount(backtest.get("grossProfit")),
                review,
            )
        )
    return ReportTable(
        "模型状态",
        (
            "模型",
            "算法",
            "Qlib日期",
            "覆盖率",
            "样本(训/验/测)",
            "IC/RankIC",
            "毛收益",
            "最近评估",
        ),
        tuple(rows),
        "毛收益使用配置的 2,000 元回测资金，不扣固定买卖费用。",
    )


def order_report(
    config: AppConfig,
    limit: int,
    start_date: date | None = None,
    end_date: date | None = None,
) -> ReportTable:
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
    return ReportTable(
        f"最近{limit}条已闭环交易",
        (
            "股票代码",
            "股票名称",
            "买入日",
            "卖出日",
            "持仓交易日",
            "数量",
            "买入价",
            "卖出价",
            "真实费用",
            "毛收益",
            "毛收益率",
            "净收益",
            "净收益率",
            "模型",
        ),
        tuple(closed),
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


# AI_GENERATE_END -----------------------
