# 分析量化模型页面接口方案

## 一、前端页面数据需求

「策略分析中心」页面（`/analysis`）当前展示：

| 数据项 | 说明 | 来源 |
|--------|------|------|
| 策略列表 | 选股策略(1 条) + 交易策略(5 条) | 接口 |
| 每条策略 | id, type(selection/trading), name, **active**, **winRate**, **pnl**, **trades** | 接口 |
| 操作 | 切换策略启用/禁用 (Switch) | 接口 |
| 汇总 | 启用策略数、总 P&L | 前端根据列表计算 |

## 二、与后端现状对应

- **选股策略**：仅一条「双因子模型」，对应整体策略开关 `StrategyStateDto.enabled`。
- **交易策略**：对应日内卖出指标——移动止损(trailingStop)、RSI(rsi)、成交量(volume)、布林带(bollinger)；启用状态由 `StrategyStateManager.disabledIndicators` 决定（不在列表中即为启用）。T+1 为整体框架，可视为常开。
- **胜率 / PnL / 交易次数**：当前无按策略维度的统计存储，回测为整体 Mock，**首期用占位数据**，后续可接入真实回测或决策流水。

## 三、接口设计

### 1. GET /api/strategy/analysis/list

返回分析页所需的策略列表（选股 + 交易）。

**响应体**（JSON 数组，每项）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 策略唯一标识，与前端一致：doubleFactor, tplus1, stopLoss, rsi, volume, bollinger |
| type | string | "selection" \| "trading" |
| nameKey | string | 前端 i18n 的 key，如 strategyAnalysis.selection.doubleFactor |
| active | boolean | 是否启用 |
| winRate | number | 胜率 (0–100)，占位 |
| pnl | number | 累计盈亏（元），占位 |
| totalTrades | number | 交易次数，占位 |

**id 与后端映射**：

- `doubleFactor` → 选股；active = `StrategyStateDto.enabled`
- `tplus1` → 无对应开关，固定 active=true
- `stopLoss` → indicator `trailingStop`
- `rsi` → indicator `rsi`
- `volume` → indicator `volume`
- `bollinger` → indicator `bollinger`

### 2. PUT /api/strategy/analysis/{id}/active

更新某策略的启用状态。

**路径参数**：id（同上）

**请求体**：

```json
{ "active": true }
```

**逻辑**：

- `doubleFactor`：更新整体策略开关（在 StrategyStateManager 中增加对 enabled 的读写）。
- `tplus1`：可不处理或返回 200（始终启用）。
- `stopLoss` / `rsi` / `volume` / `bollinger`：根据 active 调用 `enableIndicator` / `disableIndicator`。

## 四、后端实现要点

1. 新增 DTO：`AnalysisStrategyItemDto`（id, type, nameKey, active, winRate, pnl, totalTrades）。
2. 在 `StrategyController` 中新增：`GET /api/strategy/analysis/list`、`PUT /api/strategy/analysis/{id}/active`。
3. `StrategyStateManager`：支持整体 enabled 的 get/set；与前端 id 的映射常量集中在一处（如 `AnalysisStrategyConstants` 或 Controller 内）。
4. 胜率/PnL/交易数：首期返回 0 或从现有 BacktestResultDto 取全局值做占位；后续再接入分策略统计。

## 五、前端对接说明

- 列表接口返回的 `nameKey` 用于 `t(nameKey)` 显示名称；若后端改为直接返回 `name` 也可。
- 汇总「当前启用数」「总 P&L」由前端根据列表的 `active` 和 `pnl` 计算，无需单独接口。
