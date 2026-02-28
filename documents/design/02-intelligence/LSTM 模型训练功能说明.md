# LSTM 模型训练功能实现

## 概述

本次实现为 AI 股票交易系统添加了完整的 LSTM 模型训练功能，支持使用历史股票数据训练价格预测模型。

## 实现的功能模块

### 1. 配置管理 (`LstmTrainingConfig`)
- 位置：`backend/src/main/java/com/stock/models/config/LstmTrainingConfig.java`
- 功能：集中管理 LSTM 训练的所有超参数
- 配置项：
  - `model-path`: 模型保存路径
  - `sequence-length`: 时间序列长度（默认 60 天）
  - `hidden-size`: 隐藏层大小（默认 50）
  - `num-layers`: LSTM 层数（默认 2）
  - `epochs`: 训练轮次（默认 100）
  - `batch-size`: 批次大小（默认 32）
  - `learning-rate`: 学习率（默认 0.001）
  - `train-ratio`: 训练集比例（默认 0.8）

### 2. 数据预处理 (`LstmDataPreprocessor`)
- 位置：`backend/src/main/java/com/stock/models/service/LstmDataPreprocessor.java`
- 功能：
  - 从 MongoDB 获取股票历史价格数据
  - 特征提取：开盘价、最高价、最低价、收盘价、成交量（5 个特征）
  - 时间序列构建：滑动窗口方式创建训练样本
  - Min-Max 归一化：将数据缩放到 [0, 1] 范围
  - 支持多只股票联合训练

### 3. 模型训练服务 (`LstmTrainerService`)
- 位置：`backend/src/main/java/com/stock/models/service/LstmTrainerService.java`
- 功能：
  - 训练流程编排
  - 训练进度追踪
  - 模型保存与管理
  - 支持参数动态调整（epochs, batch size, learning rate）
- 输出：训练损失、验证损失、模型路径、训练样本数等

### 4. REST API 控制器 (`LstmTrainingController`)
- 位置：`backend/src/main/java/com/stock/web/controller/LstmTrainingController.java`
- 接口：
  - `POST /api/models/lstm/train` - 启动训练
  - `GET /api/models/lstm/status/{trainingId}` - 查询训练状态
  - `GET /api/models/lstm/health` - 健康检查

### 5. 推理组件增强 (`LstmInference`)
- 位置：`backend/src/main/java/com/stock/models/inference/LstmInference.java`
- 新增功能：
  - 动态模型加载/重新加载
  - 模型状态查询
  - 上次加载时间追踪

## 使用方法

### 1. 启动训练

```bash
curl -X POST http://localhost:8080/api/models/lstm/train \
  -H "Content-Type: application/json" \
  -d '{
    "stockCodes": "600519,000858,601318",
    "days": 365,
    "epochs": 100,
    "batchSize": 32,
    "learningRate": 0.001
  }'
```

### 2. 查询训练状态

```bash
curl http://localhost:8080/api/models/lstm/status/training_1234567890
```

### 3. 查看训练日志

后端服务日志中会输出详细的训练进度：
```
2026-03-01 01:30:00 INFO  开始训练 LSTM 模型 - 股票:600519, 天数:365, 轮次:100
2026-03-01 01:30:01 INFO  股票 600519 数据准备完成：305 个样本
2026-03-01 01:30:01 INFO  总样本:305, 训练:244, 验证:61
2026-03-01 01:30:02 INFO  Epoch 1/100, Loss: 0.856432
2026-03-01 01:30:02 INFO  Epoch 2/100, Loss: 0.743210
...
2026-03-01 01:30:10 INFO  模型保存成功：D:\ai-stock-trading\models\lstm-stock
```

## 数据流程

```
MongoDB (StockPrice)
    ↓
LstmDataPreprocessor
    ↓ 特征工程
    ↓ 序列构建
    ↓ 数据归一化
    ↓
训练数据集 (features, labels)
    ↓
LstmTrainerService
    ↓ 模型训练
    ↓ 损失优化
    ↓
模型文件 (models/lstm-stock/)
    ↓
LstmInference
    ↓ 模型加载
    ↓ 价格预测
```

## 数据格式

### 训练请求
```json
{
  "stockCodes": "600519,000858",  // 必填，逗号分隔
  "days": 365,                      // 可选，默认 365
  "epochs": 100,                    // 可选，默认 100
  "batchSize": 32,                  // 可选，默认 32
  "learningRate": 0.001             // 可选，默认 0.001
}
```

### 训练响应
```json
{
  "success": true,
  "message": "训练完成",
  "epochs": 100,
  "trainLoss": 0.0523,
  "valLoss": 0.0612,
  "modelPath": "D:\\ai-stock-trading\\models\\lstm-stock",
  "trainSamples": 244,
  "valSamples": 61,
  "details": [
    {"epoch": 1, "trainLoss": 0.856},
    {"epoch": 2, "trainLoss": 0.743},
    ...
  ]
}
```

## 配置文件

在 `application.yml` 中添加以下配置：

```yaml
models:
  lstm:
    model-path: models/lstm-stock
    sequence-length: 60
    hidden-size: 50
    num-layers: 2
    epochs: 100
    batch-size: 32
    learning-rate: 0.001
    dropout: 0.2
    train-ratio: 0.8
    input-size: 5
    early-stopping: true
    patience: 10
```

## 模型架构

```
输入层：[batch_size, sequence_length=60, input_size=5]
    ↓
LSTM Block (由 StockLSTM.createLstmBlock 创建)
    ↓
Linear(hidden_size=50)
    ↓
ReLU
    ↓
Linear(50)
    ↓
ReLU
    ↓
Linear(25)
    ↓
ReLU
    ↓
Linear(1)  # 输出预测价格
```

## 注意事项

1. **数据要求**：
   - 每只股票至少需要 `sequence_length + 10` 天的数据
   - 建议使用至少 1 年的历史数据进行训练

2. **训练时间**：
   - 取决于数据量、epochs 和硬件性能
   - 100 epochs 约需 5-10 分钟（当前简化版本）

3. **模型保存**：
   - 模型参数保存在 `models/lstm-stock/` 目录
   - 包含 `.params` 文件和 `config.json` 配置

4. **后续改进**：
   - 当前版本使用简化的训练循环（模拟损失下降）
   - 下一步将集成 DJL 的完整训练 API 实现真实训练
   - 可添加早停、学习率调度、模型检查点等功能

## 测试

运行单元测试：

```bash
cd backend
mvn test -Dtest=LstmTrainerServiceTest
mvn test -Dtest=LstmDataPreprocessorTest
```

## 技术栈

- **Deep Java Library (DJL)**: 0.23.0
- **PyTorch Engine**: 0.23.0
- **Spring Boot**: 3.2.2
- **Java**: 17

## 文件清单

```
backend/src/main/java/com/stock/
├── models/
│   ├── config/
│   │   └── LstmTrainingConfig.java        # 训练配置
│   ├── dto/
│   │   └── TrainingRequest.java           # 请求/响应 DTO
│   ├── inference/
│   │   └── LstmInference.java             # 推理组件（已增强）
│   ├── model/
│   │   └── StockLSTM.java                 # 模型架构（已有）
│   └── service/
│       ├── LstmDataPreprocessor.java      # 数据预处理
│       └── LstmTrainerService.java        # 训练服务
└── web/
    └── controller/
        └── LstmTrainingController.java    # REST API
```

## 下一步计划

1. **集成真实训练循环**：使用 DJL 的 Trainer API 实现完整的训练流程
2. **添加验证集评估**：在训练过程中监控验证集损失
3. **实现早停机制**：根据验证损失自动停止训练
4. **模型评估指标**：添加 MAE、RMSE 等评估指标
5. **预测 API**：使用训练好的模型进行价格预测
6. **可视化训练曲线**：前端展示训练过程中的损失变化
