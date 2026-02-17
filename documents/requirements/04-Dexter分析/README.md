# Dexter分析模块

## 模块职责

对接Dexter基本面分析API，获取个股财务分析和走势建议，为选股提供基本面因子。

## 需求列表

### 1. Dexter基本面分析需求

| 需求ID | 需求描述 | 优先级 | 技术方案 |
|--------|----------|--------|----------|
| AR-009 | 查询个股财务分析 | P0 | Dexter HTTP API |
| AR-010 | 获取次日走势建议 | P0 | Dexter Agent |
| AR-011 | 分析结果量化评分 | P1 | 建议转分数 |
| AR-012 | 分析结果缓存 | P1 | Redis |

### 2. 分析数据

| 数据项 | 说明 |
|--------|------|
| 财务指标 | 营收、利润、PE、PB等 |
| 走势建议 | 买入/持有/卖出 |
| 风险评估 | 高/中/低 |
| 评分 | 0-100分 |

## 外部依赖

- Dexter API

## 输入接口

```java
public interface DexterService {
    DexterResult analyze(String stockCode);
    Suggestion getSuggestion(String stockCode);
    DexterResult getCached(String stockCode);
}
```

## 输出数据格式

```json
{
    "stockCode": "600519",
    "analysis": {
        "financialScore": 85,
        "growthScore": 78,
        "riskLevel": "low"
    },
    "suggestion": "buy",
    "targetPrice": 2000.00,
    "cached": false
}
```
