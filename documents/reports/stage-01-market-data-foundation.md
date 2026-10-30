---
id: stage-01-market-data-foundation
title: 阶段 01：市场数据基础报告
sidebar_position: 101
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-01-market-data-foundation/
source_paths:
  - stock-trading/pyproject.toml
  - stock-trading/src/stock_trading/cli.py
  - stock-trading/src/stock_trading/market/baostock_client.py
  - stock-trading/src/stock_trading/market/audit.py
  - stock-trading/src/stock_trading/market/storage.py
---

<DocMetadata />

# 阶段 01：市场数据基础报告

## 阶段目标

建立纯 Python 最小工程的数据入口，确认 BaoStock 能否同时承担全市场日线和 5 分钟标签数据，并冻结与真实分阶段交易一致的分钟时间边界。

## 已完成

- 建立 `stock-trading` Python 包、CLI 和依赖清单。
- 使用 BaoStock `0.9.4` 实现全市场日线、单股日线和 5 分钟数据查询。
- 支持可选 HTTP CONNECT 代理，登录或建连异常时恢复原始连接器。
- 定义每天 48 根 5 分钟 K 线及上海时区结束时间戳。
- 实现缺失 K 线、额外 K 线和分钟聚合相对偏差审计。
- 实现日线、分钟线不可变 Parquet 分区和临时文件原子替换。
- 提供 `sync-daily`、`sync-minute`、`probe-minute` 和 `show-boundaries` 命令。

## 验证证据

- 5 只股票、2025-10-20 至 2025-10-24 共 25 个股票交易日均为每天 48 根。
- 同一股票交易日重复请求 5 次，行数和内容哈希一致。
- 2026-10-29 的全市场日线返回 5,223 行。
- 全市场日线 Parquet 回读 5,223 行、5,223 个唯一代码、核心字段无空值、无重复业务键。
- `sh.600519` 的分钟 Parquet 回读 48 行，首根结束时间为 `09:35`，末根为 `15:00`，无重复业务键。
- Python 源码语法和 `pyproject.toml` 结构检查通过。

## 数据质量结论

BaoStock 的请求稳定性和时间完整性满足快速模型验证，但分钟聚合 OHLC 与日线存在非零偏差，不能把分钟数据视为官方日线的精确拆分。后续标签生成必须只消费审计通过的完整交易日，并保留来源版本和偏差记录。

标签窗口固定为：

- 下午买入 `13:05-14:54`：使用结束时间 `13:10-14:50` 的完整 5 分钟 K 线。
- 次日上午卖出 `09:35-11:25`：使用结束时间 `09:40-11:25` 的完整 5 分钟 K 线。

## 未覆盖项

- 尚未完成全市场多年历史回填和精确最早分钟日期扫描。
- 尚未安装和运行 Qlib、LightGBM，也未生成 Alpha158 特征。
- 尚未实现模型训练、Top 5、组合回测、SQLite 状态和券商 Python 客户端。
- 当前工作区存在 Python 3.13 虚拟环境，但本阶段真实数据验证使用 Python 3.12；Qlib 阶段必须重新确认运行版本。
- 未执行 Java Maven、React 构建、数据库或券商请求。

## 下一阶段

阶段 02 将建立从 Parquet 到 Qlib 的确定性转换、交易日历和股票池，运行 `Alpha158 + LightGBM Regression` 基线并输出每日 Top 5 与 IC、Rank IC 等横截面指标。
