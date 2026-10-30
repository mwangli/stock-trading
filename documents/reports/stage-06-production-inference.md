---
id: stage-06-production-inference
title: 阶段 06：生产推理闭环报告
sidebar_position: 106
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-06-production-inference/
source_paths:
  - stock-trading/src/stock_trading/research/model.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/cli.py
---

<DocMetadata />

# 阶段 06：生产推理闭环报告

## 阶段目标

补齐研究训练与每日运行之间缺失的生产推理入口：加载已经训练的 `Alpha158 + LightGBM Regression` 制品，对指定交易日全股票池重新计算横截面分数，输出 Top K，并幂等写入 SQLite。

## 已完成

- 新增 `predict-latest --date YYYY-MM-DD` 命令，生产候选固定为 Top 5，不暴露运行时数量开关。
- 只接受 Qlib 日历中真实存在的交易日，不自动把自然日猜成交易日。
- 从模型 `metrics.json` 读取原训练区间，使用相同 Alpha158 特征定义和处理流程。
- 从 `model.pkl` 读取模型，并按模型保存的特征名称重新排列 158 个输入列。
- 对全部可用股票评分，以分数降序、股票代码升序形成确定性 Top K。
- 预测制品按日期写入 `predictions/top5-YYYY-MM-DD.parquet`，使用临时文件原子替换。
- SQLite 任务使用模型 SHA-256 与预测日期形成稳定业务键。
- 同一模型同一预测制品重复执行返回 `SKIPPED`，不重复增加预测记录。
- 同一模型和日期只允许一套不可变预测；如果重算结果发生变化，不会用第二套候选覆盖已经成功的生产事实。
- 非交易日或尚未进入 Qlib 日历的日期直接失败，不创建预测状态；`2026-10-30` 已验证被拒绝。

## 真实数据验证

使用阶段 2 已生成的真实 Qlib 数据和模型，对 `2026-10-29` 执行生产推理：

```text
全股票池评分：20
Top K：5
SQLite prediction：5
模型状态：REJECTED
REAL_TRADING_ENABLED：false
```

候选顺序为：

```text
1. SZ000559
2. SZ002249
3. SH600667
4. SZ002354
5. SH600879
```

将新生产推理结果与阶段 2 的同日 `top5.parquet` 比较：

```text
股票顺序一致：true
最大分数绝对差：0.0
```

证明生产推理与训练测试阶段使用了同一特征和模型语义，没有因为重新创建 Qlib Handler 产生漂移。

## 幂等验证

第一次完整命令执行成功，SQLite 写入 1 个任务、1 个模型和 5 条预测。第二次完整执行重新计算相同分数，但状态导入返回 `SKIPPED`：

```text
task_run：1
model_version：1
prediction：5
```

## 安全边界

生产推理只生成模型事实，不调用券商，不创建真实订单意图。当前模型 Rank IC 为负，写入 SQLite 后仍为 `REJECTED`；真实交易开关继续为 `false`，因此该 Top 5 只能用于研究和影子流程。

## 为什么没有删除旧工程

阶段审计确认，Python 当前虽然已有数据、训练、回测、状态、券商契约和生产推理，但还没有以下运行链路：

- 上午读取真实可卖持仓并分阶段卖出。
- 下午从当天 Top 5 中按权限、价格、资金和订单状态选择一只并分阶段买入。
- 订单部分成交、撤单、未知状态和应用重启后的券商事实恢复。
- 收盘后把真实成交转成绩效并触发滚动评估。

此时删除 Java、React、Quartz 和旧数据库会得到更小但不能自动交易的仓库。因此最终替换收口必须后移，不能用“代码更少”替代“业务闭环完整”。

## 验证边界

- 已执行 Python 语法检查、真实 Qlib 推理、制品比较和 SQLite 重复执行验证。
- 没有执行模型重训；使用的是阶段 2 已训练模型。
- 没有执行真实券商 HTTP 请求或委托。
- 未执行 Java Maven、React 编译、构建或启动。

## 下一阶段

阶段 07 实现最小影子交易编排，只保留上午卖出、下午买入、单持仓、Top 5 顺序、100 股整手、T+1、订单终态和恢复，不引入 Web、消息队列或工作流框架。
