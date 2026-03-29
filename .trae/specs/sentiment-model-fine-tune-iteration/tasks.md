# Tasks: 情感分析模型微调迭代闭环

## 任务列表

### Phase 1: 文档更新（已完成）

- [x] Task 1: 更新需求文档 (需求.md)
- [x] Task 2: 更新设计文档 (设计.md)

### Phase 2: 代码实现（已完成）

- [x] Task 3: MongoDB Collections 和 Repository 实现
  - [x] SentimentEvaluation 实体
  - [x] SentimentAutoLabel 实体
  - [x] SentimentModelVersion 实体
  - [x] 对应 Repository 接口

- [x] Task 4: ModelEvaluationService 实现
  - [x] 评估指标计算（准确率/F1/夏普比率等）
  - [x] 阈值判定逻辑
  - [x] 评估结果存储

- [x] Task 5: AutoLabelService 实现
  - [x] 基于交易反馈的自动标注逻辑
  - [x] 标注规则（收益率>1%利好，<-1%利空）
  - [x] 标注数据质量过滤

- [x] Task 6: ModelVersionService 实现
  - [x] 模型版本保存
  - [x] 版本回滚功能
  - [x] 版本列表查询

- [x] Task 7: Controller 接口实现
  - [x] ModelEvaluationController
  - [x] ModelVersionController

- [x] Task 8: Job 定时任务实现
  - [x] SentimentModelEvaluationJob
  - [x] SentimentAutoLabelJob
  - [x] JobBootstrap 任务注册

- [x] Task 9: 配置和参数化
  - [x] SentimentEvaluationConfig 配置类

## 验收标准

- [x] 编译通过，无语法错误 (BUILD SUCCESS)
- [x] 评估指计算逻辑正确
- [x] 自动标注规则符合文档定义
- [x] 模型版本管理功能可用
- [x] 定时任务正确注册

## 创建的文件清单

| 文件 | 路径 |
|------|------|
| SentimentEvaluation.java | domain/entity/ |
| SentimentAutoLabel.java | domain/entity/ |
| SentimentModelVersion.java | domain/entity/ |
| SentimentEvaluationRepository.java | persistence/ |
| SentimentAutoLabelRepository.java | persistence/ |
| SentimentModelVersionRepository.java | persistence/ |
| ModelEvaluationService.java | service/ |
| AutoLabelService.java | service/ |
| ModelVersionService.java | service/ |
| ModelEvaluationController.java | api/ |
| ModelVersionController.java | api/ |
| SentimentModelEvaluationJob.java | job/ |
| SentimentAutoLabelJob.java | job/ |
| SentimentEvaluationConfig.java | config/ |
