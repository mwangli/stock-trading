# 决策引擎模块

## 模块职责

根据综合选股结果和风控检查结果，生成最终交易信号，控制交易执行时机。

## 需求列表

### 1. 交易信号需求

| 需求ID | 需求描述 | 优先级 |
|--------|----------|--------|
| TR-005 | 信号类型：买入/不买入 | P0 |
| TR-006 | 信号置信度计算 | P1 |
| TR-007 | 信号有效期：仅当日有效 | P0 |

### 2. 决策因素

| 因素 | 来源 | 权重 |
|------|------|------|
| 综合评分 | 选股模块 | 60% |
| 风控状态 | 风控模块 | 40% |

### 3. 决策规则

```
IF 风控状态 = 正常 AND 综合评分 >= 阈值 THEN
    生成买入信号
ELSE IF 风控状态 = 暂停 THEN
    生成不买入信号
ELSE
    生成观望信号
```

## 依赖模块

- 综合选股模块
- 风控管理模块

## 输入接口

```java
public interface DecisionEngine {
    TradingSignal generateSignal(AnalysisResult analysis);
    Decision makeDecision(StockSelectionResult selection);
}
```

## 输出数据格式

```json
{
    "signal": "buy",
    "stockCode": "600519",
    "quantity": 100,
    "confidence": 0.75,
    "validUntil": "2024-01-15T14:55:00",
    "reason": "综合评分85分，风控正常"
}
```
