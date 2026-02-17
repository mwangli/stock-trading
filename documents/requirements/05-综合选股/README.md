# 综合选股模块

## 模块职责

整合情感分析、LSTM预测、Dexter分析三方评分，计算综合得分选出最优股票。

## 需求列表

### 1. 综合选股需求

| 需求ID | 需求描述 | 优先级 |
|--------|----------|--------|
| TR-001 | 综合LSTM、情感、Dexter三方评分 | P0 |
| TR-002 | 选股权重：LSTM 40%、情感 30%、Dexter 30% | P0 |
| TR-003 | 每日选出综合评分最高的1只 | P0 |
| TR-004 | 备选股票池（Top3） | P1 |

### 2. 评分算法

```
综合得分 = (LSTM排名 × 0.4) + (情感排名 × 0.3) + (Dexter排名 × 0.3)

说明：
- 各因子先转换为排名（1-N）
- 加权计算综合得分
- 得分越低越优
```

### 3. 选股流程

```
1. 获取目标股票池（自选股/全市场）
2. 并行调用三个分析模块
3. 计算各因子排名
4. 加权计算综合得分
5. 排序选出Top1 + Top3备选
```

## 依赖模块

- 数据采集模块
- 情感分析模块
- LSTM预测模块
- Dexter分析模块

## 输入接口

```java
public interface StockSelector {
    SelectResult selectBestStock();
    List<ComprehensiveScore> getComprehensiveRanking();
}
```

## 输出数据格式

```json
{
    "selectedStock": {
        "stockCode": "600519",
        "stockName": "贵州茅台",
        "score": 15.5,
        "rank": 1
    },
    "alternatives": [
        {"stockCode": "000858", "stockName": "五粮液", "score": 18.2, "rank": 2},
        {"stockCode": "601318", "stockName": "中国平安", "score": 20.1, "rank": 3}
    ],
    "factors": {
        "lstm": {"score": 0.75, "rank": 2},
        "sentiment": {"score": 0.82, "rank": 1},
        "dexter": {"score": 78, "rank": 3}
    }
}
```
