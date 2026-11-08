---
id: business-model-matrix-design
title: 模型矩阵与策略标签设计
sidebar_position: 25
owner: business
type: design
status: ACCEPTED
last_verified: 2026-11-08
slug: /business/model-matrix-design/
source_paths:
  - stock-trading@src/stock_trading/research
  - stock-trading@src/stock_trading/trading
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-client/src/desktop/pages/modelInfo
---

<!-- AI_GENERATE_START ---- -->
# 模型矩阵与策略标签设计

## 1. 设计目标

模型体系需要同时解决四个问题：

1. 明确每个模型能够使用的数据范围，禁止未来数据和高等级数据越级进入低等级模型。
2. 保证训练标签、回测价格、RANK 决策和真实交易时间具有相同业务含义。
3. 使用统一模型编号和评估口径，支持不同数据等级与算法的横向、纵向比较。
4. 在先卖后买的资金约束下，通过 RANK 淘汰缓冲和动态委托降低无效换仓与交易成本。

最终模型由两个可比较维度组成：

```text
数据等级 FeatureLevel × 算法 Algorithm
```

策略标签不再作为任意组合维度。每个数据等级固定对应一个策略标签，避免产生数据能力与交易合同不一致的无效组合：

```text
FeatureLevel -> StrategyLabel
```

这种设计把原本可能无限扩张的“数据等级 × 算法 × 标签”组合收敛为清晰矩阵。新增算法时增加一列，新增数据等级时增加一行；同一行始终使用同一个标签合同。

## 2. 核心概念

### 2.1 数据等级

数据等级按能力递进，高等级可以使用低等级数据，低等级禁止使用高等级数据。

| 等级 | 数据能力 | 决策时点 |
|---|---|---|
| L1 | 完整日线 | 前一交易日收盘后 |
| L2 | 日线、完整 5 分钟线 | 前一交易日收盘后 |
| L3 | 日线、5 分钟线、1 分钟线和当日上午分钟数据 | 当日午间 |

等级边界是强约束：

- L1 不读取 5 分钟、1 分钟和午间数据。
- L2 不读取 1 分钟数据，也不假设能够在 11:30 获得午间增量数据。
- L3 可以使用 L1、L2 的历史数据，并使用当天上午的 1 分钟数据形成午间特征。
- 五档实时行情属于委托执行事实，不属于模型训练特征，因此各等级在真实交易时都可以读取五档价格。

### 2.2 模型编号

模型编号只表达数据能力和算法：

```text
Ln_数据频率_算法名称
```

第一阶段模型编号固定为：

```text
L1_daily_LightGBM
L1_daily_XGBoost
L2_minute5_LightGBM
L2_minute5_XGBoost
```

模型编号不包含版本号、日期、标签或训练批次。训练日期、参数、数据快照和制品版本属于一次独立运行 `runId`，不能继续膨胀模型编号。

### 2.3 策略标签

策略标签不是任意收益公式，而是完整交易合同。合同至少包括：

```text
特征截止时间
RANK 生成时间
买入价格基准
卖出价格基准
标签周期
执行先后顺序
最低数据等级
RANK 保留规则
数据完整性要求
```

准备、训练、回测、排名、交易和评估必须引用同一个策略合同。制品保存 `strategyId` 和 `strategyContractHash`；合同不一致时直接拒绝使用，不提供旧标签兼容、自动降级或隐式转换。

## 3. 固定等级与标签映射

### 3.1 L1：全天日均价策略

```text
strategyId: T1_ALL_BUY_ALL_SELL
displayName: 全天动态执行、日均价 T+1
featureLevel: L1
labelHorizon: T+1
```

L1 使用日线成交额和成交量计算日成交量加权平均价：

```text
daily_vwap = amount / volume
```

按实际买入日 `T` 定义标签：

```text
label(T, stock) = daily_vwap(T+1, stock) / daily_vwap(T, stock) - 1
```

由于完整日线只能在收盘后获得，特征日 `D` 对齐到实际交易时为：

```text
feature(D) -> buy benchmark(D+1) -> sell benchmark(D+2)
label(D, stock) = daily_vwap(D+2, stock) / daily_vwap(D+1, stock) - 1
```

真实执行不要求在单一开盘点或收盘点成交。交易日内先处理被 RANK 淘汰的持仓，卖出成交并确认资金后，再使用剩余时间买入新候选。五档定价根据剩余时间逐步提高主动性。

日 VWAP 是模型研究基准，不是假定真实订单一定成交在该价格。实际成交价与日 VWAP 的偏差作为执行质量单独记录。

### 3.2 L2：上午卖出、下午买入策略

```text
strategyId: T1_PM_BUY_AM_SELL
displayName: 收盘排名、下午买入、次日上午卖出
featureLevel: L2
labelHorizon: T+1
```

L2 在前一交易日收盘后使用完整日线和完整 5 分钟数据生成 RANK，不依赖当天午间增量数据。

按实际买入日 `T` 定义标签：

```text
buy_vwap(T) = vwap(T, 14:00-15:00)
sell_vwap(T+1) = vwap(T+1, 09:30-11:30)
label(T, stock) = sell_vwap(T+1, stock) / buy_vwap(T, stock) - 1
```

按特征日 `D` 对齐：

```text
feature(D) -> buy(D+1 afternoon) -> sell(D+2 morning)
```

每日执行顺序固定为：

```text
上午卖出被淘汰持仓
-> 查询成交并同步真实可用资金
-> 下午买入新候选
```

上午未成交的资金不得计入下午买入额度。买卖窗口都使用 5 分钟成交额和成交量计算 VWAP，窗口不完整或成交量为零时标签无效，不使用开盘价、收盘价或临近数据补齐。

### 3.3 L3：午间分钟决策策略

```text
strategyId: T1_NOON_RANK_PM_BUY_NEXT_PM_SELL
displayName: 午间分钟决策、下午先卖后买
featureLevel: L3
labelHorizon: T+1
status: PLANNED
```

L3 使用当天 09:30-11:30 的 1 分钟数据形成午间特征，在 11:30 后生成新 RANK：

```text
09:30-11:30 采集上午分钟数据
11:30-12:15 生成特征和 RANK
13:00-14:00 卖出被淘汰持仓
14:00-15:00 买入新候选
```

规划标签为：

```text
label(D, stock)
= vwap(D+1, 13:00-14:00, stock)
  / vwap(D, 14:00-15:00, stock)
  - 1
```

L3 在分钟数据覆盖率、连续性和执行窗口质量达到要求前只保留设计，不进入第一阶段训练、回测和生产矩阵。

## 4. RANK 淘汰与低换手设计

三个等级使用相同的 RANK 缓冲思想，但在各自策略规定的时间执行。

初始参数：

```text
entryRank = 5
retainRank = 15
```

规则：

- 空仓时，只从 `entryRank` 范围内依次选择可成交候选。
- 已持仓股票排名在 `retainRank` 范围内时继续持有，不因短期排名波动换股。
- 已持仓股票跌出 `retainRank` 后，才进入对应策略的卖出流程。
- 卖出完成并确认资金后，才允许买入新候选。
- 第一候选不可成交时，按完整排名依次检查下一候选。
- 停牌、权限、涨跌停、价格、资金和券商结果属于执行可行性，不改变模型分数。

`entryRank < retainRank` 形成滞回区间。股票从第 5 名下降到第 6 至第 15 名不会触发换仓，可以减少边缘排名反复变化造成的手续费和滑点。

模型标签始终是每日滚动 T+1 标签。实际持仓可能因连续留在保留区而跨多个交易日，但不会因此生成 T+n 训练标签。真实收益仍以最初买入到最终卖出的券商成交闭环计算。

## 5. 五档动态执行

五档行情用于提高真实成交率，不能进入模型训练特征或历史标签。

卖出阶段按剩余时间逐步提高紧迫度：

```text
低紧迫度：优先改善价格
中紧迫度：靠近买一提高成交概率
高紧迫度：结合买一至买五数量选择有效限价
```

买入阶段使用相反方向：

```text
低紧迫度：优先改善价格
中紧迫度：靠近卖一提高成交概率
高紧迫度：结合卖一至卖五数量选择有效限价
```

每次重新报价都必须重新读取券商事实、确认已有成交和撤单结果、计算剩余数量，并使用新的 `clientOrderId`。部分成交只对剩余数量重新委托。

五档执行解决的是成交概率和成交价格问题，不改变标签价格。执行质量通过实际成交价相对标签基准 VWAP 的偏差单独评价。

## 6. 第一阶段 2×2 矩阵

第一阶段只实现 L1、L2 和 LightGBM、XGBoost：

| 数据等级 | LightGBM | XGBoost | 固定策略标签 |
|---|---|---|---|
| L1 daily | `L1_daily_LightGBM` | `L1_daily_XGBoost` | `T1_ALL_BUY_ALL_SELL` |
| L2 minute5 | `L2_minute5_LightGBM` | `L2_minute5_XGBoost` | `T1_PM_BUY_AM_SELL` |

第一阶段不包含：

- L3 模型训练和回测。
- 第三种算法。
- 标签自由组合。
- 旧模型编号、旧标签和旧目录兼容。
- 根据缺失数据自动降级到其他等级。
- 一个模型同时绑定多个策略标签。

实现采用当前设计作为唯一权威结构。不能复用的数据和制品直接删除后重新采集、准备或训练，不保留前向兼容分支。

## 7. 第二阶段 3×3 规划

分钟数据完整后，矩阵扩展为三个数据等级和三种算法：

| 数据等级 | LightGBM | XGBoost | 第三算法 | 固定策略标签 |
|---|---|---|---|---|
| L1 daily | 启用 | 启用 | 规划 | `T1_ALL_BUY_ALL_SELL` |
| L2 minute5 | 启用 | 启用 | 规划 | `T1_PM_BUY_AM_SELL` |
| L3 minute1 | 规划 | 规划 | 规划 | `T1_NOON_RANK_PM_BUY_NEXT_PM_SELL` |

第三算法只有在明确训练成本、推理稳定性和增益后再确定，不为凑齐矩阵提前引入依赖。

## 8. 评估原则

### 8.1 可直接比较的范围

同一数据等级、同一标签合同下的不同算法可以直接横向比较，例如：

```text
L1_daily_LightGBM vs L1_daily_XGBoost
L2_minute5_LightGBM vs L2_minute5_XGBoost
```

它们使用相同特征能力、交易时间和标签价格，差异主要来自算法。

### 8.2 跨等级比较

L1 与 L2 的交易窗口和标签基准不同，不能仅凭收益率判断哪个模型算法更强。跨等级比较的是完整策略质量，需要同时展示：

```text
累计毛收益率
年化毛收益率
最大毛回撤
胜率
Rank IC / ICIR
换手率
完整闭环数量
平均持有天数
买入成交率
卖出成交率
实际成交价相对标签 VWAP 偏差
资金使用率
因 RANK 缓冲减少的换仓次数
```

研究回测按毛利润、毛收益率和毛回撤评价模型，不扣固定每笔费用。真实交易按券商成交闭环计算净收益，包含实际佣金和税费。两套口径必须分别命名和展示。

## 9. 数据与制品存储

建议目录保持按事实层、特征层和模型层分离：

```text
data/
├── market/
│   ├── daily/
│   ├── minute5/
│   └── minute1/
├── features/
│   ├── L1_daily/
│   ├── L2_minute5/
│   └── L3_minute1/
├── targets/
│   ├── T1_ALL_BUY_ALL_SELL/
│   ├── T1_PM_BUY_AM_SELL/
│   └── T1_NOON_RANK_PM_BUY_NEXT_PM_SELL/
├── models/
│   └── <modelId>/<runId>/
└── state/
    └── trading.db
```

模型运行至少保存：

```text
modelId
runId
featureLevel
algorithm
strategyId
strategyContractHash
featureSchemaHash
dataSnapshot
trainRange
validationRange
testRange
parameters
metrics
artifactPath
createdAt
```

SQLite 保存模型定义、训练运行、回测结果、激活状态和真实收益归属；Parquet 保存不可变市场事实和研究数据；模型二进制与元数据保存在对应 `runId` 目录。

## 10. PC 模型矩阵页面

PC 页面以矩阵为第一视图：

- 行表示数据等级。
- 列表示算法。
- 单元格表示一个固定 `modelId` 的当前激活运行。
- L3 和第三算法在未实现前显示“规划中”，不能伪造收益或空模型记录。

每个已实现单元格展示：

```text
模型编号
固定策略标签
运行状态
训练数据截止日
回测累计毛收益率
最大毛回撤
胜率
换手率
真实已实现毛收益
最后评估时间
```

页面支持按资金场景和回测区间切换，但不提供训练、激活、交易或参数修改按钮。Java/React 控制台只读取 Python 写入的 SQLite 和制品事实。

点击单元格后显示模型详情、策略合同、训练区间、回测指标和真实交易指标。相同标签的算法对比使用统一尺度；跨等级页面必须提示交易合同不同。

## 11. 设计收益

该设计具备以下优点：

1. 数据等级与标签一一对应，消除不可能执行或数据越级的组合。
2. 模型编号稳定，新增算法和数据等级时矩阵自然扩展。
3. 标签、回测和真实交易时间一致，降低研究结果失真。
4. 先卖后买保证只使用已经确认的可用资金。
5. RANK 滞回区间减少无效换仓、手续费和滑点。
6. 五档动态执行提高成交率，同时不污染模型特征边界。
7. 2×2 先验证最有价值的四个模型，避免分钟数据不足时提前增加复杂度。
8. 不保留旧结构兼容代码，目录、配置和运行合同保持简单清晰。

## 12. 实施顺序

第一阶段按以下顺序落地：

1. 固化 L1、L2 数据等级和固定策略标签配置。
2. 删除 L3 运行入口和标签自由组合逻辑，仅保留规划定义。
3. 按新目录和运行元数据重构准备、训练、回测和排名链路。
4. 建立四个固定模型定义和独立训练运行记录。
5. 回测保存统一指标与资金场景结果。
6. SQLite 提供只读模型矩阵事实。
7. PC 页面展示 2×2 矩阵和 L3 规划状态。
8. 数据完整后再评估 L3 和第三算法，不提前保留兼容占位代码。

本文记录目标设计。具体能力只有在对应源码完成并经过相应验证后，才能视为已实现。
<!-- AI_GENERATE_END ---- -->
