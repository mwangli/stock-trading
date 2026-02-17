# 情感分析模块

## 模块职责

使用FinBERT模型对财经新闻进行情感分析，计算股票情感得分，为选股提供情绪因子。

## 需求列表

### 1. 情感分析需求

| 需求ID | 需求描述 | 优先级 | 技术方案 |
|--------|----------|--------|----------|
| AR-001 | 对财经新闻进行情感分析 | P0 | FinBERT |
| AR-002 | 计算股票情感得分（-1到1） | P0 | FinBERT输出映射 |
| AR-003 | 聚合多新闻情感得分 | P0 | 加权平均算法 |
| AR-004 | 情感分析结果排序选股 | P1 | 作为选股因子 |

### 2. 情感得分计算

| 指标 | 说明 |
|------|------|
| 输入 | 新闻标题+正文 |
| 输出 | positive/negative/neutral + score |
| 得分范围 | -1 (negative) ~ +1 (positive) |

## 技术方案

- **模型**: ProsusAI/finbert
- **框架**: PyTorch + Transformers
- **部署**: Python FastAPI服务

## 外部依赖

- Python AI服务 (py-service)

## 输入接口

```java
public interface SentimentAnalysisService {
    SentimentResult analyze(String text);
    double calculateStockSentiment(String stockCode);
    List<StockScore> getStockSentimentRanking();
}
```

## 输出数据格式

```json
{
    "label": "positive",
    "score": 0.85,
    "confidence": 0.92,
    "probabilities": {
        "positive": 0.85,
        "neutral": 0.10,
        "negative": 0.05
    }
}
```
