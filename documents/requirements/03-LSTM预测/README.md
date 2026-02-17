# LSTM预测模块

## 模块职责

使用LSTM神经网络预测股票价格走势，为选股提供技术因子。

## 需求列表

### 1. LSTM价格预测需求

| 需求ID | 需求描述 | 优先级 | 技术方案 |
|--------|----------|--------|----------|
| AR-005 | 预测次日涨跌（二分类） | P0 | PyTorch LSTM |
| AR-006 | 融合情感得分作为输入特征 | P0 | 多模态输入 |
| AR-007 | 预测涨幅概率 | P0 | Sigmoid输出 |
| AR-008 | 模型定期训练与更新 | P0 | 每周离线训练 |

### 2. 预测输出

| 指标 | 说明 |
|------|------|
| 输入 | 60日OHLCV数据 + 情感得分 |
| 输出 | 次日预测价格、涨跌方向、置信度 |
| 预测天数 | 1-5天 |

### 3. 模型要求

| 指标 | 要求 |
|------|------|
| 序列长度 | 60天 |
| 特征维度 | 5 (OHLCV) + 1 (情感得分) |
| 更新频率 | 每周 |
| 训练数据 | 近3年历史数据 |

## 技术方案

- **模型**: LSTM (2层, 100隐藏单元)
- **框架**: TensorFlow/Keras
- **部署**: Python FastAPI服务

## 外部依赖

- Python AI服务 (py-service)
- 数据采集模块

## 输入接口

```java
public interface PredictionService {
    PredictionResult predict(String stockCode);
    List<PredictionResult> predictBatch(List<String> stockCodes);
    ForecastResult forecast(String stockCode, int days);
}
```

## 输出数据格式

```json
{
    "stockCode": "600519",
    "predictedPrice": 1850.50,
    "currentPrice": 1820.00,
    "change": 30.50,
    "changePercent": 1.68,
    "direction": "up",
    "confidence": 0.75
}
```
