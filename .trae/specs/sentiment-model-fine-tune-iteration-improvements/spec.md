# 情感分析模型微调迭代闭环 - 改进优化

## Why

上一阶段实现的情感分析模型自我迭代功能存在以下问题需要解决：
1. `SentimentEvaluationConfig` 配置类已创建但未被 `ModelEvaluationService` 使用（阈值硬编码）
2. 评估功能使用固定模拟数据，未对接真实交易系统
3. `AutoLabelService.fetchActualTradingFeedback()` 返回空列表
4. 微调触发后的完整闭环未打通

## What Changes

- **修复**：`ModelEvaluationService` 引用 `SentimentEvaluationConfig` 配置类
- **修复**：`AutoLabelService` 引入 `SentimentEvaluationConfig` 配置参数
- **优化**：对接 `TradingExecutorService` 获取真实 T+1 交易数据
- **优化**：`ModelEvaluationService` 使用标注数据作为评估数据集
- **增强**：`SentimentModelEvaluationJob` 微调触发后发送告警

## Impact

- **受影响的代码**：
  - `ModelEvaluationService` - 注入配置、改进评估数据来源
  - `AutoLabelService` - 注入配置、使用真实交易数据
  - `SentimentModelEvaluationJob` - 微调触发告警
  - `TradingExecutorService` - 新增 T+1 数据查询接口

## MODIFIED Requirements

### Requirement: 配置化阈值管理

**原实现**：阈值使用硬编码常量

**修改为**：
- `ModelEvaluationService` 注入 `SentimentEvaluationConfig`
- 阈值从配置类读取，支持运行时调整
- 保持向后兼容（配置缺失时使用默认值）

### Requirement: 真实数据评估

**原实现**：使用15条固定模拟测试数据

**修改为**：
- 使用 `SentimentAutoLabelRepository` 查询已验证的标注数据
- 确保评估数据集具有统计代表性（≥100条）
- 回退机制：标注数据不足时使用原有模拟数据

### Requirement: 自动标注数据对接

**原实现**：`fetchActualTradingFeedback()` 返回空列表

**修改为**：
- 对接 `TradingExecutorService` 查询 T+1 已结算交易记录
- 计算买卖价差收益率
- 关联新闻文本（通过 `StockNews` 或类似实体）

## ADDED Requirements

### Requirement: 微调触发告警

系统 SHALL 在评估结果触发微调条件时发送告警通知：
- 记录 WARN 级别日志（已有）
- 扩展：发送内部通知或邮件（可选）

#### Scenario: 微调触发
- **WHEN** `shouldFineTune = true` 且标注样本充足
- **THEN** 系统记录告警日志，标注需要人工介入或自动触发微调

## Key Parameters (继承)

| 参数 | 默认值 | 来源 |
|------|--------|------|
| `accuracyThreshold` | 70% | SentimentEvaluationConfig |
| `f1Threshold` | 60% | SentimentEvaluationConfig |
| `minSamplesForFineTune` | 500 | SentimentEvaluationConfig |
| `minConfidenceForLabel` | 0.6 | SentimentEvaluationConfig |

## Implementation Notes

1. **配置注入优先级**：`ModelEvaluationService` 和 `AutoLabelService` 应通过构造函数注入配置
2. **数据回退策略**：标注数据不足时记录警告日志，使用模拟数据
3. **服务依赖**：需要确认 `TradingExecutorService` 是否存在及其接口签名
