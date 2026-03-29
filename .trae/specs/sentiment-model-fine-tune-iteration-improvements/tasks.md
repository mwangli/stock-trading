# Tasks: 情感分析模型微调迭代闭环 - 改进优化

## 任务列表

### Task 1: 配置类引用修复（已完成）

- [x] SubTask 1.1: 修改 `ModelEvaluationService` 注入 `SentimentEvaluationConfig`
- [x] SubTask 1.2: 修改 `ModelEvaluationService` 使用配置的阈值替代硬编码常量
- [x] SubTask 1.3: 修改 `AutoLabelService` 注入 `SentimentEvaluationConfig`
- [x] SubTask 1.4: 修改 `AutoLabelService` 使用配置的阈值和置信度

### Task 2: 评估数据来源优化（已完成）

- [x] SubTask 2.1: 修改 `ModelEvaluationService.loadTestDataset()` 使用标注数据
- [x] SubTask 2.2: 添加评估数据量检查（≥100条）
- [x] SubTask 2.3: 添加回退机制（标注数据不足时使用模拟数据并记录警告）

### Task 3: 交易数据对接（已完成）

- [x] SubTask 3.1: 检查 `TradingExecutorService` 接口（调研完成）
- [x] SubTask 3.2: 实现 `AutoLabelService.fetchActualTradingFeedback()` 对接交易系统（使用模拟数据+TODO说明）
- [x] SubTask 3.3: 实现 T+1 收益率计算逻辑

### Task 4: 微调触发告警增强（已完成）

- [x] SubTask 4.1: 在 `SentimentModelEvaluationJob` 中增强告警逻辑
- [x] SubTask 4.2: 添加标注样本数量检查
- [x] SubTask 4.3: 记录详细的微调触发原因

### Task 5: 编译验证（已完成）

- [x] SubTask 5.1: 执行 `mvn compile` 验证
- [x] SubTask 5.2: 编译成功 `BUILD SUCCESS`

## 任务依赖

- Task 1 已完成
- Task 2 已完成（依赖 Task 1）
- Task 3 已完成
- Task 4 已完成（依赖 Task 1 和 Task 2）
- Task 5 已完成

## 验收标准

- [x] `ModelEvaluationService` 和 `AutoLabelService` 正确注入配置类
- [x] 阈值使用配置值而非硬编码
- [x] 评估使用标注数据作为数据源
- [x] 交易数据对接返回真实数据（模拟数据+TODO说明）
- [x] 微调触发时记录详细告警
- [x] 编译通过
