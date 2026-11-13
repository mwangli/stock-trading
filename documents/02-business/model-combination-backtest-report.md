---
id: business-model-combination-backtest-report
title: 模型矩阵组合回测报告
sidebar_position: 26
owner: business
type: report
status: ACCEPTED
last_verified: 2026-11-13
slug: /business/model-combination-backtest-report/
source_paths:
  - stock-trading@data/models/backtests/experiment-grid-report.md
  - stock-trading@data/models/backtests/experiment-grid-results.csv
---

<!-- AI_GENERATE_START ----- -->
# 模型矩阵组合回测报告

## 1. 报告范围

本报告记录 2026-11-13 完成的模型、交易策略和资金参数组合回测。回测结果保存在 Python 交易项目的 `data/models/backtests/<backtest_id>/`，本报告只复制研究结论和关键指标，不复制模型二进制、Parquet 或 SQLite 文件。

- 第一阶段：4 个模型 × 5 个策略，共 20 组。
- 第二阶段：2 个模型 × 2 个策略 × 5 个资金档位，共 20 次执行。
- 其中 4 次与第一阶段完全相同的合同按稳定 `backtest_id` 幂等复用。
- 最终形成 36 个唯一实验目录，SQLite `backtest_run` 登记数为 36。
- 测试区间：L1 为 `2026-05-28` 至 `2026-11-06`；L2 为 `2026-06-03` 至 `2026-11-10`。
- 收益评价以毛收益和毛回撤为主；手续费只作为附加事实，不用于模型优劣排序。

## 2. 模型与标签

| 模型 | 数据能力 | 算法 | 模型参数 | 标签合同 |
|---|---|---|---|---|
| `L1_daily_LightGBM` | 完整日线 | LightGBM | `learning_rate=0.05`、`num_leaves=31`、`max_depth=6`、`n_estimators=300` | `daily_vwap(D+2) / daily_vwap(D+1) - 1` |
| `L1_daily_XGBoost` | 完整日线 | XGBoost | 训练制品中的固定算法参数 | `daily_vwap(D+2) / daily_vwap(D+1) - 1` |
| `L2_minute5_LightGBM` | 日线 + 完整 5 分钟线 | LightGBM | `learning_rate=0.05`、`num_leaves=31`、`max_depth=6`、`n_estimators=300` | `daily_vwap(D+2) / daily_vwap(D+1) - 1` |
| `L2_minute5_XGBoost` | 日线 + 完整 5 分钟线 | XGBoost | 训练制品中的固定算法参数 | `daily_vwap(D+2) / daily_vwap(D+1) - 1` |

标签中的 `D` 是预测日，`D+1` 是买入基准交易日，`D+2` 是卖出基准交易日。标签预测的是一个交易日收益排序，不是 7 天持仓收益；`maxHoldingDays` 属于回测退出策略。

## 3. 策略参数含义

- `exitPosition`：经过资金、权限、价格和可交易性过滤后，持仓股仍在该排名以内时继续持有。
- `exitFallbackPosition`：持仓股掉出 `exitPosition` 但仍在该排名以内时进入观察区。
- `rankGraceDays`：处于观察区时允许继续观察的交易日数。
- `maxHoldingDays`：达到最大持仓天数后强制卖出。
- 候选使用全市场 RANK，不限制为固定 Top5 买入；实际买入仍受资金、整手、权限和可成交性约束。

## 4. 最高收益组合

绝对毛收益最高组合为：

```text
model=L1_daily_LightGBM
exitPosition=5
exitFallbackPosition=20
rankGraceDays=1
maxHoldingDays=7
capital=20000
```

- 毛收益率：`188.85%`
- 最大毛回撤：`-36.99%`
- 胜率：`54.00%`
- 成交笔数：`50`
- 实验：`bt_20261113_l1-daily-lightgbm_dd9f7caacad3`

2000 元资金下的最高收益组合仍是同一策略合同：

- 毛收益率：`157.51%`
- 最大毛回撤：`-28.78%`
- 胜率：`58.97%`
- 成交笔数：`39`
- 平均持仓天数：`2.77`
- 实验：`bt_20261113_l1-daily-lightgbm_2b3309565d82`

## 5. 完整实验结果

表中收益和回撤均为百分比；实验目录由 `backtest_id` 唯一标识。

| 模型 | 资金 | 退出排名 | 兜底排名 | 观察天数 | 最大持仓 | 毛收益率 | 最大回撤 | 胜率 | 成交笔数 | backtest_id |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| L1_daily_LightGBM | 1000 | 5 | 20 | 1 | 7 | -53.89% | -56.59% | 36.36% | 33 | `bt_20261113_l1-daily-lightgbm_0f65366f321c` |
| L1_daily_LightGBM | 2000 | 5 | 20 | 1 | 7 | 157.51% | -28.78% | 58.97% | 39 | `bt_20261113_l1-daily-lightgbm_2b3309565d82` |
| L1_daily_LightGBM | 5000 | 5 | 20 | 1 | 7 | 127.75% | -38.15% | 51.02% | 49 | `bt_20261113_l1-daily-lightgbm_61485c83ffb4` |
| L1_daily_LightGBM | 10000 | 5 | 20 | 1 | 7 | 171.13% | -37.13% | 54.00% | 50 | `bt_20261113_l1-daily-lightgbm_3f378c895870` |
| L1_daily_LightGBM | 20000 | 5 | 20 | 1 | 7 | 188.85% | -36.99% | 54.00% | 50 | `bt_20261113_l1-daily-lightgbm_dd9f7caacad3` |
| L1_daily_LightGBM | 1000 | 10 | 20 | 1 | 5 | -19.92% | -25.44% | 50.00% | 38 | `bt_20261113_l1-daily-lightgbm_fc8057a27eb1` |
| L1_daily_LightGBM | 2000 | 10 | 20 | 1 | 5 | 4.46% | -36.74% | 52.38% | 42 | `bt_20261113_l1-daily-lightgbm_f19047b5402f` |
| L1_daily_LightGBM | 5000 | 10 | 20 | 1 | 5 | 68.99% | -32.07% | 54.72% | 53 | `bt_20261113_l1-daily-lightgbm_ab88283dbdf0` |
| L1_daily_LightGBM | 10000 | 10 | 20 | 1 | 5 | 43.02% | -35.97% | 52.94% | 51 | `bt_20261113_l1-daily-lightgbm_5e8783d35831` |
| L1_daily_LightGBM | 20000 | 10 | 20 | 1 | 5 | 45.71% | -36.28% | 54.00% | 50 | `bt_20261113_l1-daily-lightgbm_18b9abffa28c` |
| L1_daily_LightGBM | 2000 | 5 | 10 | 0 | 5 | 31.33% | -36.74% | 46.15% | 52 | `bt_20261113_l1-daily-lightgbm_45aeb1463d6a` |
| L1_daily_LightGBM | 2000 | 5 | 20 | 0 | 3 | 102.47% | -26.26% | 56.00% | 50 | `bt_20261113_l1-daily-lightgbm_6f2f9f00e6d3` |
| L1_daily_LightGBM | 2000 | 5 | 20 | 0 | 5 | 4.46% | -36.74% | 52.38% | 42 | `bt_20261113_l1-daily-lightgbm_632650b92f38` |
| L1_daily_XGBoost | 2000 | 5 | 20 | 1 | 3 | 22.94% | -31.16% | 49.02% | 51 | `bt_20261113_l1-daily-xgboost_628c261a3b81` |
| L1_daily_XGBoost | 2000 | 5 | 20 | 1 | 5 | 24.89% | -39.80% | 39.02% | 41 | `bt_20261113_l1-daily-xgboost_43bd28d41607` |
| L1_daily_XGBoost | 2000 | 5 | 20 | 1 | 7 | -1.98% | -39.94% | 38.46% | 39 | `bt_20261113_l1-daily-xgboost_0b4a71a050d6` |
| L1_daily_XGBoost | 2000 | 5 | 10 | 0 | 5 | 35.81% | -32.43% | 46.15% | 52 | `bt_20261113_l1-daily-xgboost_58af5e097d06` |
| L1_daily_XGBoost | 2000 | 10 | 20 | 1 | 5 | 24.89% | -39.80% | 39.02% | 41 | `bt_20261113_l1-daily-xgboost_e5d2fb71aee5` |
| L2_minute5_LightGBM | 1000 | 5 | 20 | 1 | 7 | -36.38% | -41.07% | 32.26% | 31 | `bt_20261113_l2-minute5-lightgbm_995a4c099f52` |
| L2_minute5_LightGBM | 2000 | 5 | 20 | 1 | 7 | -4.76% | -26.94% | 37.14% | 35 | `bt_20261113_l2-minute5-lightgbm_d34b7d6a02bd` |
| L2_minute5_LightGBM | 5000 | 5 | 20 | 1 | 7 | 19.01% | -35.70% | 46.34% | 41 | `bt_20261113_l2-minute5-lightgbm_1ead1dc26708` |
| L2_minute5_LightGBM | 10000 | 5 | 20 | 1 | 7 | -5.19% | -36.06% | 48.65% | 37 | `bt_20261113_l2-minute5-lightgbm_c9d3b5866732` |
| L2_minute5_LightGBM | 20000 | 5 | 20 | 1 | 7 | -5.07% | -37.92% | 48.72% | 39 | `bt_20261113_l2-minute5-lightgbm_324014ca0d08` |
| L2_minute5_LightGBM | 1000 | 10 | 20 | 1 | 5 | -8.97% | -25.92% | 37.50% | 32 | `bt_20261113_l2-minute5-lightgbm_f60efdf93837` |
| L2_minute5_LightGBM | 2000 | 10 | 20 | 1 | 5 | 35.10% | -32.87% | 40.00% | 30 | `bt_20261113_l2-minute5-lightgbm_d342c951a5b4` |
| L2_minute5_LightGBM | 5000 | 10 | 20 | 1 | 5 | -15.69% | -29.90% | 37.14% | 35 | `bt_20261113_l2-minute5-lightgbm_1817b925be2d` |
| L2_minute5_LightGBM | 10000 | 10 | 20 | 1 | 5 | -40.37% | -48.94% | 37.84% | 37 | `bt_20261113_l2-minute5-lightgbm_263a064de64a` |
| L2_minute5_LightGBM | 20000 | 10 | 20 | 1 | 5 | -45.12% | -54.64% | 42.11% | 38 | `bt_20261113_l2-minute5-lightgbm_7ae8c3315280` |
| L2_minute5_LightGBM | 2000 | 5 | 10 | 0 | 5 | 27.59% | -29.69% | 38.10% | 42 | `bt_20261113_l2-minute5-lightgbm_3c2037f6f1f7` |
| L2_minute5_LightGBM | 2000 | 5 | 20 | 1 | 3 | -18.41% | -29.18% | 44.23% | 52 | `bt_20261113_l2-minute5-lightgbm_8ee2e77264a3` |
| L2_minute5_LightGBM | 2000 | 5 | 20 | 1 | 5 | -2.22% | -28.79% | 36.11% | 36 | `bt_20261113_l2-minute5-lightgbm_5b1c8178a9d5` |
| L2_minute5_XGBoost | 2000 | 5 | 20 | 1 | 3 | 39.82% | -42.75% | 51.11% | 45 | `bt_20261113_l2-minute5-xgboost_4a26ee465418` |
| L2_minute5_XGBoost | 2000 | 5 | 20 | 1 | 5 | 19.68% | -31.73% | 44.44% | 36 | `bt_20261113_l2-minute5-xgboost_85145aa10f35` |
| L2_minute5_XGBoost | 2000 | 5 | 20 | 1 | 7 | 34.01% | -31.49% | 30.00% | 30 | `bt_20261113_l2-minute5-xgboost_b339ad16aceb` |
| L2_minute5_XGBoost | 2000 | 10 | 20 | 1 | 5 | 19.07% | -31.79% | 47.06% | 34 | `bt_20261113_l2-minute5-xgboost_93dacf031b58` |
| L2_minute5_XGBoost | 2000 | 5 | 10 | 0 | 5 | -4.04% | -45.05% | 35.90% | 39 | `bt_20261113_l2-minute5-xgboost_0bb8f6bd5cfb` |

## 6. 评估结论与启用建议

1. 绝对毛收益最高的是 `L1_daily_LightGBM` + `exit5/fallback20/grace1/max7`，但最大回撤约 `-37%`，不能仅凭毛收益直接实盘启用。
2. 2000 元资金下，该组合毛收益率为 `157.51%`，是本次所有 2000 元组合中的最高值；对应回撤仍为 `-28.78%`。
3. L1 LightGBM 明显优于 L1 XGBoost 和本次测试中的 L2 组合；L2 增加分钟特征没有在当前测试区间转化为更高的稳定收益。
4. 本次组合没有同时满足最大回撤不超过 `15%`、胜率不低于 `45%` 的硬性条件，因此当前结论只能作为研究候选，不应直接作为真实交易启用结论。
5. 后续应使用未参与本轮参数选择的时间区间复验，并进行模拟盘、小资金实盘和逐笔成交事实核对。

<!-- AI_GENERATE_END ----- -->
