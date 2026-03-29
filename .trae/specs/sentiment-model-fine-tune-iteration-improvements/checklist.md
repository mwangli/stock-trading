# Checklist: 情感分析模型微调迭代闭环 - 改进优化

## Task 1: 配置类引用修复

- [x] `ModelEvaluationService` 正确注入 `SentimentEvaluationConfig`
- [x] `ModelEvaluationService` 使用 `config.getAccuracyThreshold()` 等方法替代硬编码常量
- [x] `AutoLabelService` 正确注入 `SentimentEvaluationConfig`
- [x] `AutoLabelService` 使用 `config.getMinConfidenceForLabel()` 等方法替代硬编码常量

## Task 2: 评估数据来源优化

- [x] `ModelEvaluationService.loadTestDataset()` 从 `SentimentAutoLabelRepository` 读取标注数据
- [x] 评估数据量检查逻辑（≥100条）
- [x] 回退机制：数据不足时使用模拟数据并记录警告

## Task 3: 交易数据对接

- [x] `TradingExecutorService` 接口存在或已创建（调研完成）
- [x] `AutoLabelService.fetchActualTradingFeedback()` 实现对接逻辑（模拟数据+TODO说明）
- [x] T+1 收益率计算正确

## Task 4: 微调触发告警增强

- [x] `SentimentModelEvaluationJob` 检查 `shouldFineTune` 和标注样本数量
- [x] 微调触发时记录包含详细原因
- [x] 标注不足时记录警告

## Task 5: 编译验证

- [x] `mvn compile` 执行成功
- [x] 无新增编译错误
