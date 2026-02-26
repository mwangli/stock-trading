# 纯Java技术方案设计文档

## 文档信息

| 属性 | 内容 |
|------|------|
| 文档编号 | ARCH-2026-001 |
| 文档名称 | 纯Java AI股票交易系统技术方案 |
| 版本 | 1.0 |
| 状态 | 初稿 |
| 创建日期 | 2026-02-26 |
| 作者 | mwangli |

---

## 一、可行性分析

### 1.1 当前Python技术栈

| 模块 | 技术 | 功能 |
|------|------|------|
| 情感分析 | FinBERT (PyTorch) | 金融文本情感分析 |
| LSTM预测 | PyTorch | 股价预测 |
| 数据采集 | akshare/baostock | 股票数据获取 |
| 模型迭代 | Python训练 | 模型训练更新 |
| Web服务 | FastAPI | API接口 |
| 任务调度 | APScheduler | 定时任务 |

### 1.2 替代可行性评估

| 模块 | 替代难度 | 方案 | 精度影响 |
|------|---------|------|---------|
| 情感分析 | **中高** | DL4J/ONNX Runtime | 可持平 |
| LSTM预测 | **中** | DL4J/ONNX Runtime | 可持平 |
| 数据采集 | **低** | Java HTTP Client | 无影响 |
| 模型迭代 | **高** | Java训练框架 | 需要验证 |
| 任务调度 | **低** | Spring Quartz | 无影响 |

### 1.3 结论

**完全剔除Python服务和代码在技术上是可行的**，但需要注意：

1. **模型训练**：需要使用Java框架重新训练模型，或通过ONNX格式转换现有模型
2. **数据采集**：Java生态有丰富的HTTP客户端，可完全替代
3. **实施工作量**：预计需要2-3个月的开发周期

---

## 二、整体架构设计

### 2.1 架构对比

#### 当前架构 (Python + Java)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户层 (React Frontend)                         │
│                              http://localhost:8000                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           应用层 (双服务架构)                                 │
│                                                                             │
│  ┌─────────────────────────────┐      ┌─────────────────────────────┐       │
│  │    Java Backend (8080)     │      │   Python AI Service (8001) │       │
│  │                            │      │                            │       │
│  │  • 数据采集                │      │  • FinBERT 情感分析       │       │
│  │  • 综合选股                │      │  • LSTM 价格预测          │       │
│  │  • 决策引擎                │      │  • 模型迭代               │       │
│  │  • 交易执行                │      │                            │       │
│  │  • 风控管理                │      │                            │       │
│  └─────────────────────────────┘      └─────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 目标架构 (纯Java)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户层 (React Frontend)                         │
│                              http://localhost:8000                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           应用层 (纯Java服务)                                │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                    Java Backend (8080)                               │    │
│  │                                                                     │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │    │
│  │  │   数据采集模块   │  │   AI推理模块     │  │  业务逻辑模块   │  │    │
│  │  │                 │  │                 │  │                 │  │    │
│  │  │ • HTTP数据获取  │  │ • ONNX Runtime  │  │ • 综合选股      │  │    │
│  │  │ • 数据存储      │  │ • DL4J 推理     │  │ • 决策引擎      │  │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘  │    │
│  │                                                                     │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │    │
│  │  │  任务调度模块    │  │  训练模块        │  │  交易执行模块   │  │    │
│  │  │                 │  │                 │  │                 │  │    │
│  │  │ • Spring Quartz │  │ • DL4J 训练     │  │ • 券商API集成   │  │    │
│  │  │ • 定时任务      │  │ • 模型评估      │  │ • 风控管理      │  │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 模块重构映射

| 原Python模块 | Java替代模块 | 技术方案 |
|-------------|-------------|---------|
| `sentiment_analysis.py` | `SentimentAnalysisService` | ONNX Runtime / DL4J |
| `lstm_prediction.py` | `LSTMPredictionService` | ONNX Runtime / DL4J |
| `data_collection_service.py` | `DataCollectionService` | Java HTTP Client |
| `model_iteration.py` | `ModelTrainingService` | DL4J |
| `FastAPI (app/main.py)` | - | 移除（不需要） |
| `APScheduler` | `Spring Quartz` | 已有 |

---

## 三、技术选型详细方案

### 3.1 情感分析模块

#### 3.1.1 方案对比

| 方案 | 精度 | 开发难度 | 性能 | 推荐度 |
|------|------|---------|------|-------|
| **ONNX Runtime + FinBERT** | ★★★★★ | 低 | 快 | **推荐** |
| **DL4J 自研模型** | ★★★★ | 高 | 中 | 备选 |
| Stanford CoreNLP | ★★★ | 低 | 快 | 不推荐 |

#### 3.1.2 推荐方案：ONNX Runtime

**架构设计**：

```
┌─────────────────────────────────────────────────────────────────┐
│                  SentimentAnalysisService                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                  OnnxModelManager                         │   │
│  │  • loadModel("finbert.onnx")                            │   │
│  │  • model pooling (singleton)                              │   │
│  │  • GPU/CPU 自动切换                                       │   │
│  └───────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                  Tokenizer                                │   │
│  │  • BERT Tokenizer (Java实现)                             │   │
│  │  • vocab.json 加载                                        │   │
│  │  • tokenization + padding                                │   │
│  └───────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                  InferenceEngine                          │   │
│  │  • createSession()                                       │   │
│  │  • runInference()                                        │   │
│  │  • softmax + argmax                                      │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**模型导出流程**：

```python
# Python端一次性操作
import torch
from transformers import AutoModelForSequenceClassification

# 加载FinBERT
model = AutoModelForSequenceClassification.from_pretrained('ProsusAI/finbert')

# 导出ONNX
torch.onnx.export(
    model,
    (torch.randint(0, 30000, (1, 512)),),  # dummy input
    "finbert.onnx",
    input_names=['input_ids'],
    output_names=['logits'],
    dynamic_axes={'input_ids': {0: 'batch'}, 'logits': {0: 'batch'}}
)
```

**Java实现关键类**：

```java
// 1. OnnxModelManager - 模型加载管理
public class OnnxModelManager {
    private OrtEnvironment environment;
    private OrtSession session;
    
    public void loadModel(String modelPath) {
        environment = OrtEnvironment.getEnvironment();
        session = environment.createSession(modelPath, new SessionOptions());
    }
}

// 2. BertTokenizer - 分词器
public class BertTokenizer {
    private Map<String, Integer> vocab;
    
    public int[] tokenize(String text) { /* 实现 */ }
}

// 3. SentimentInference - 推理服务
public class SentimentInference {
    public SentimentResult analyze(String text) {
        // 1. tokenize
        // 2. create tensor
        // 3. run inference
        // 4. parse result
    }
}
```

#### 3.1.3 备选方案：DL4J自研

如果无法使用ONNX，可使用DL4J从头训练：

```java
// DL4J情感分析网络
MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
    .seed(42)
    .weightInit(WeightInit.XAVIER)
    .updater(new Adam(0.001))
    .list()
    .layer(0, new EmbeddingLayer.Builder()
        .nIn(vocabSize).nOut(128)
        .activation(Activation.RELU)
        .build())
    .layer(1, new LSTM.Builder()
        .nIn(128).nOut(64)
        .activation(Activation.TANH)
        .build())
    .layer(2, new DenseLayer.Builder()
        .nIn(64).nOut(32)
        .activation(Activation.RELU)
        .build())
    .layer(3, new OutputLayer.Builder()
        .nIn(32).nOut(3)
        .activation(Activation.SOFTMAX)
        .lossFunction(LossFunctions.LossFunction.MCXENT)
        .build())
    .build();
```

**劣势**：
- 需要重新准备训练数据
- 训练时间较长
- 精度可能低于FinBERT

---

### 3.2 LSTM预测模块

#### 3.2.1 方案对比

| 方案 | 精度 | 开发难度 | 推荐度 |
|------|------|---------|-------|
| **ONNX Runtime + PyTorch LSTM** | ★★★★★ | 低 | **推荐** |
| **DL4J LSTM** | ★★★★ | 中 | 备选 |

#### 3.2.2 推荐方案：ONNX Runtime

**模型导出**：

```python
# Python端一次性操作
import torch
import torch.nn as nn

# 重新定义LSTM模型（与现有一致）
class LSTMM(nn.Module):
    def __init__(self, input_size=5, hidden_size=100, num_layers=2):
        super().__init__()
        self.lstm = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True)
        self.fc1 = nn.Linear(hidden_size, 50)
        self.fc2 = nn.Linear(50, 25)
        self.fc3 = nn.Linear(25, 1)
    
    def forward(self, x):
        lstm_out, _ = self.lstm(x)
        out = lstm_out[:, -1, :]
        out = torch.relu(self.fc1(out))
        out = torch.relu(self.fc2(out))
        out = self.fc3(out)
        return out

# 导出ONNX
model = LSTMM()
torch.onnx.export(model, torch.randn(1, 60, 5), "lstm_stock.onnx",
    input_names=['input'], output_names=['output'])
```

**Java推理**：

```java
public class LSTMInference {
    
    public PredictionResult predict(String stockCode, List<StockRecord> data) {
        // 1. 数据预处理
        INDArray input = preprocess(data);
        
        // 2. 创建序列
        INDArray[] sequences = createSequences(input, 60);
        
        // 3. ONNX推理
        OrtTensor inputTensor = environment.createTensor(
            new long[]{sequences.length, 60, 5},
            sequences.flatten().toFloatArray()
        );
        
        // 4. 运行推理
        float[] output = runInference(inputTensor);
        
        // 5. 反归一化
        float predictedPrice = inverseTransform(output[0]);
        
        return PredictionResult.builder()
            .stockCode(stockCode)
            .predictedPrice(predictedPrice)
            .confidence(0.75)
            .build();
    }
}
```

---

### 3.3 数据采集模块

#### 3.3.1 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| HTTP Client | **OkHttp** | 高性能HTTP客户端 |
| JSON解析 | **Jackson** | Spring内置 |
| 数据存储 | MyBatis-Plus | 已有 |

#### 3.3.2 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                   DataCollectionService                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │ StockDataClient │  │  NewsClient     │  │  QuoteClient   │   │
│  │                 │  │                 │  │                 │   │
│  │ • akshare API   │  │ • 东方财富新闻  │  │ • 实时行情     │   │
│  │ • 东方财富      │  │ • 新浪财经      │  │ • 涨跌幅       │   │
│  │ • Baostock      │  │                 │  │                 │   │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘   │
│           │                    │                    │            │
│           └────────────────────┼────────────────────┘            │
│                                ▼                                 │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │                    DataRepository                         │    │
│  │  • MyBatis-Plus (MySQL)                                  │    │
│  │  • MongoTemplate (MongoDB)                              │    │
│  │  • RedisTemplate (Redis)                                 │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.3.3 HTTP数据源集成

**东方财富股票列表**：

```java
@Service
public class StockDataClient {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public List<StockInfo> fetchStockList() {
        // 调用东方财富API
        String url = "https://push2.eastmoney.com/api/qt/clist/get";
        String response = httpClient.newCall(new Request.Builder()
            .url(url + "?pn=1&pz=5000&po=1&np=1")
            .get().build()).execute().body().string();
        
        // 解析JSON
        // ...
        return stocks;
    }
}
```

**Baostock历史数据**：

```java
public List<StockPrice> fetchHistoricalData(String stockCode, LocalDate start, LocalDate end) {
    // 调用Baostock API
    String url = String.format(
        "https://api.baostock.com/bs/api/query-kline-data?code=sz.%s&start=%s&end=%s",
        stockCode, start, end
    );
    // 解析返回数据
}
```

---

### 3.4 模型训练模块

#### 3.4.1 方案设计

由于DL4J训练时间序列模型相对复杂，推荐以下方案：

| 阶段 | 方案 | 说明 |
|------|------|------|
| **短期** | ONNX模型转换 | Python训练 → ONNX导出 → Java推理 |
| **长期** | DL4J训练 | Java端实现完整训练流程 |

#### 3.4.2 短期方案：模型转换流程

```
Python训练环境                              Java生产环境
┌─────────────────────┐                ┌─────────────────────┐
│                     │                │                     │
│  1. 训练模型        │                │                     │
│     (PyTorch)       │                │                     │
│         │           │                │                     │
│         ▼           │                │                     │
│  2. 导出ONNX        │   复制文件     │                     │
│     torch.onnx      │ ──────────────▶ │  3. 加载推理       │
│                     │                │     ONNX Runtime    │
│                     │                │                     │
└─────────────────────┘                └─────────────────────┘
```

**实施步骤**：

1. 在Python环境完成模型训练和优化
2. 导出ONNX格式模型
3. 将模型文件复制到Java项目 resources/models/
4. Java端通过ONNX Runtime加载推理
5. 定期更新模型文件（无需重新部署代码）

#### 3.4.3 长期方案：DL4J训练（可选）

```java
public class LSTMModelTrainer {
    
    public void train(String stockCode, List<StockPrice> trainingData) {
        // 1. 数据预处理
        INDArray features = extractFeatures(trainingData);
        INDArray labels = extractLabels(trainingData);
        
        // 2. 构建LSTM网络
        MultiLayerNetwork network = buildLSTM();
        
        // 3. 训练
        network.fit(features, labels, epochs);
        
        // 4. 保存模型
        ModelSerializer.writeModel(network, modelFile, true);
    }
    
    private MultiLayerNetwork buildLSTM() {
        return new MultiLayerNetwork(new NeuralNetConfiguration.Builder()
            .list()
            .layer(0, new GravesLSTM.Builder()
                .nIn(5).nOut(100)
                .activation(Activation.TANH)
                .build())
            .layer(1, new DenseLayer.Builder()
                .nIn(100).nOut(1)
                .activation(Activation.IDENTITY)
                .build())
            .build());
    }
}
```

---

## 四、Maven依赖配置

### 4.1 核心依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>online.mwang</groupId>
    <artifactId>stock-trading</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.2.2</spring-boot.version>
        <onnxruntime.version>1.16.3</onnxruntime.version>
        <dl4j.version>1.0.0-M2.1</dl4j.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot (已有) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        <!-- ONNX Runtime (推荐) -->
        <dependency>
            <groupId>ai.onnxruntime</groupId>
            <artifactId>onnxruntime</artifactId>
            <version>${onnxruntime.version}</version>
        </dependency>
        
        <!-- DL4J (备选/训练用) -->
        <dependency>
            <groupId>org.deeplearning4j</groupId>
            <artifactId>deeplearning4j-core</artifactId>
            <version>${dl4j.version}</version>
        </dependency>
        <dependency>
            <groupId>org.nd4j</groupId>
            <artifactId>nd4j-native-platform</artifactId>
            <version>${dl4j.version}</version>
        </dependency>
        
        <!-- HTTP Client -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>
        
        <!-- JSON处理 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.16.0</version>
        </dependency>
        
        <!-- MyBatis-Plus (已有) -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>3.5.5</version>
        </dependency>
        
        <!-- MongoDB (已有) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        
        <!-- Redis (已有) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 4.2 依赖说明

| 依赖 | 版本 | 用途 |
|------|------|------|
| onnxruntime | 1.16.3 | AI模型推理（推荐） |
| deeplearning4j-core | 1.0.0-M2.1 | 深度学习训练/推理（备选） |
| nd4j-native-platform | 1.0.0-M2.1 | NDArray矩阵运算 |
| okhttp | 4.12.0 | HTTP数据采集 |
| jackson-databind | 2.16.0 | JSON解析 |

---

## 五、数据模型设计

### 5.1 新增实体类

#### 5.1.1 模型信息表

```java
@Data
@TableName("ml_model_info")
public class MlModelInfo {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String modelName;        // 模型名称: finbert, lstm
    private String modelType;        // 模型类型: sentiment, prediction
    private String modelVersion;     // 模型版本
    private String modelPath;        // 模型文件路径
    private String modelFormat;      // 模型格式: onnx, dl4j
    private String status;           // ACTIVE, INACTIVE
    private Double accuracy;         // 准确率
    private LocalDateTime trainDate; // 训练日期
    private String description;      // 描述
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

#### 5.1.2 训练任务记录

```java
@Data
@TableName("training_task")
public class TrainingTask {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String taskName;         // 任务名称
    private String modelType;       // 模型类型
    private String stockCode;       // 股票代码（可选）
    private Integer epoch;          // 训练轮数
    private Double learningRate;    // 学习率
    private String status;          // PENDING, RUNNING, COMPLETED, FAILED
    private Double finalAccuracy;   // 最终准确率
    private String errorMessage;    // 错误信息
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
```

### 5.2 数据库表结构

```sql
-- AI模型信息表
CREATE TABLE `ml_model_info` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `model_name` varchar(50) NOT NULL COMMENT '模型名称',
    `model_type` varchar(20) NOT NULL COMMENT '模型类型: sentiment/prediction',
    `model_version` varchar(20) DEFAULT NULL COMMENT '模型版本',
    `model_path` varchar(255) NOT NULL COMMENT '模型文件路径',
    `model_format` varchar(20) DEFAULT 'onnx' COMMENT '模型格式: onnx/dl4j',
    `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `accuracy` double DEFAULT NULL COMMENT '准确率',
    `train_date` datetime DEFAULT NULL COMMENT '训练日期',
    `description` varchar(500) DEFAULT NULL COMMENT '描述',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_name` (`model_name`, `model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型信息表';

-- 训练任务记录表
CREATE TABLE `training_task` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `task_name` varchar(100) NOT NULL COMMENT '任务名称',
    `model_type` varchar(20) NOT NULL COMMENT '模型类型',
    `stock_code` varchar(10) DEFAULT NULL COMMENT '股票代码',
    `epoch` int(11) DEFAULT 0 COMMENT '训练轮数',
    `learning_rate` double DEFAULT 0.001 COMMENT '学习率',
    `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态',
    `final_accuracy` double DEFAULT NULL COMMENT '最终准确率',
    `error_message` text COMMENT '错误信息',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练任务记录表';
```

---

## 六、服务接口设计

### 6.1 情感分析接口

#### 6.1.1 单条文本分析

```
POST /api/sentiment/analyze

Request:
{
    "text": "贵州茅台发布财报，营收同比增长15%"
}

Response:
{
    "code": 200,
    "data": {
        "label": "positive",
        "score": 0.92,
        "confidence": 0.92,
        "probabilities": {
            "positive": 0.92,
            "neutral": 0.05,
            "negative": 0.03
        }
    }
}
```

#### 6.1.2 批量分析

```
POST /api/sentiment/analyze/batch

Request:
{
    "texts": [
        "text1",
        "text2"
    ]
}

Response:
{
    "code": 200,
    "data": [
        {"label": "positive", "score": 0.92},
        {"label": "neutral", "score": 0.65}
    ]
}
```

### 6.2 LSTM预测接口

#### 6.2.1 次日价格预测

```
POST /api/lstm/predict

Request:
{
    "stockCode": "600519",
    "data": [
        {"date": "2024-01-01", "open": 10.5, "high": 10.8, "low": 10.4, "close": 10.6, "volume": 1000000},
        ... (至少60条)
    ]
}

Response:
{
    "code": 200,
    "data": {
        "stockCode": "600519",
        "predictedPrice": 1850.5,
        "currentPrice": 1820.0,
        "change": 30.5,
        "changePercent": 1.68,
        "direction": "up",
        "confidence": 0.75,
        "isTrained": true
    }
}
```

#### 6.2.2 批量预测

```
POST /api/lstm/predict/batch

Request:
{
    "stocks": [
        {"stockCode": "600519", "data": [...]},
        {"stockCode": "000858", "data": [...]}
    ]
}

Response:
{
    "code": 200,
    "data": [
        {"stockCode": "600519", "predictedPrice": 1850.5, ...},
        {"stockCode": "000858", "predictedPrice": 156.8, ...}
    ]
}
```

### 6.3 模型管理接口

#### 6.3.1 模型信息查询

```
GET /api/ml/model/info?modelType=sentiment

Response:
{
    "code": 200,
    "data": {
        "modelName": "finbert",
        "modelType": "sentiment",
        "modelVersion": "v1.0",
        "modelFormat": "onnx",
        "status": "ACTIVE",
        "accuracy": 0.89,
        "trainDate": "2026-01-15"
    }
}
```

#### 6.3.2 模型更新

```
POST /api/ml/model/update

Request:
{
    "modelType": "sentiment",
    "modelFile": "finbert_new.onnx",
    "description": "更新版FinBERT模型"
}

Response:
{
    "code": 200,
    "message": "模型更新成功"
}
```

---

## 七、定时任务设计

### 7.1 任务清单

| 任务ID | 任务名称 | 执行时间 | 说明 |
|--------|----------|---------|------|
| Task-001 | 数据采集-股票列表 | 每日 09:00 | 更新股票列表 |
| Task-002 | 数据采集-历史数据 | 每日 09:30 | 更新历史K线 |
| Task-003 | 数据采集-实时行情 | 每分钟 | 更新实时行情 |
| Task-004 | 情感分析 | 每日 15:30 | 分析当日新闻情感 |
| Task-005 | LSTM预测 | 每日 16:00 | 预测次日价格 |
| Task-006 | 综合选股 | 每日 16:30 | 生成选股建议 |
| Task-007 | 交易执行 | 每日 09:35 | 执行买入 |
| Task-008 | 风控检查 | 实时 | 风险控制 |

### 7.2 Spring Scheduler配置

```java
@Configuration
@EnableScheduling
public class SchedulerConfig {
    
    @Scheduled(cron = "0 0 9 * * 1-5")  // 每日9:00
    public void syncStockList() {
        // 同步股票列表
    }
    
    @Scheduled(cron = "0 30 15 * * 1-5")  // 每日15:30
    public void runSentimentAnalysis() {
        // 情感分析
    }
    
    @Scheduled(cron = "0 0 16 * * 1-5")  // 每日16:00
    public void runLSTMPrediction() {
        // LSTM预测
    }
    
    @Scheduled(cron = "0 */1 * * * *")  // 每分钟
    public void syncRealtimeQuotes() {
        // 实时行情
    }
}
```

---

## 八、实施计划

### 8.1 阶段划分

| 阶段 | 时间 | 内容 | 交付物 |
|------|------|------|--------|
| **Phase 1** | 第1周 | 需求分析和技术验证 | 技术方案确认 |
| **Phase 2** | 第2-3周 | ONNX模型导出和测试 | finbert.onnx, lstm.onnx |
| **Phase 3** | 第4-6周 | Java推理模块开发 | SentimentService, LSTMService |
| **Phase 4** | 第7-8周 | 数据采集模块开发 | DataCollectionService |
| **Phase 5** | 第9-10周 | 定时任务和集成 | 完整服务集成 |
| **Phase 6** | 第11-12周 | 测试和优化 | 性能测试报告 |

### 8.2 详细任务

#### Phase 1: 技术验证 (第1周)

- [ ] 验证ONNX Runtime Java集成
- [ ] 测试FinBERT模型ONNX导出
- [ ] 验证LSTM模型ONNX导出
- [ ] 确认性能指标

#### Phase 2: 模型准备 (第2-3周)

- [ ] 导出FinBERT为ONNX格式
- [ ] 导出LSTM为ONNX格式
- [ ] 测试Python端ONNX推理精度
- [ ] 准备分词器资源文件

#### Phase 3: 推理模块开发 (第4-6周)

- [ ] 实现OnnxModelManager
- [ ] 实现BertTokenizer
- [ ] 实现SentimentAnalysisService
- [ ] 实现LSTMPredictionService
- [ ] 单元测试

#### Phase 4: 数据采集开发 (第7-8周)

- [ ] 实现StockDataClient (HTTP)
- [ ] 实现NewsClient
- [ ] 实现QuoteClient
- [ ] 集成MyBatis/MongoDB/Redis

#### Phase 5: 集成测试 (第9-10周)

- [ ] 配置Spring Scheduler
- [ ] 集成测试
- [ ] 性能优化
- [ ] 文档编写

#### Phase 6: 部署上线 (第11-12周)

- [ ] 功能测试
- [ ] 性能测试
- [ ] 部署文档
- [ ] 上线部署

---

## 九、风险评估

### 9.1 技术风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| ONNX模型精度损失 | 中 | 验证推理精度，确保误差<1% |
| DL4J性能不足 | 低 | 使用ONNX Runtime替代 |
| Java分词器准确性 | 中 | 使用开源Java BERT分词器 |
| 内存占用过高 | 中 | 模型按需加载，及时释放 |

### 9.2 进度风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 模型导出失败 | 低 | Python脚本导出，预先测试 |
| 第三方API不稳定 | 中 | 添加重试机制和降级方案 |
| 性能优化周期长 | 中 | 预留缓冲时间 |

---

## 十、附录

### 10.1 参考资源

| 资源 | 链接 |
|------|------|
| ONNX Runtime Java | https://onnxruntime.ai/docs/how-to/inference.html#java |
| DL4J 官方文档 | https://deeplearning4j.org/ |
| BERT分词器Java实现 | https://github.com/hpcaitech/bert4j |
| OkHttp文档 | https://square.github.io/okhttp/ |

### 10.2 模型文件结构

```
src/main/resources/
├── models/
│   ├── finbert.onnx          # 情感分析模型
│   ├── finbert-vocab.txt     # 词汇表
│   ├── lstm_stock.onnx       # LSTM预测模型
│   └── lstm_scaler.json      # 归一化参数
└── config/
    └── application.yml        # 配置文件
```

### 10.3 性能指标目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 情感分析单条 | < 200ms | 含分词+推理 |
| LSTM预测单条 | < 100ms | 含数据处理+推理 |
| 批量分析(100条) | < 5s | 并行推理 |
| 模型加载 | < 3s | 首次加载 |
| 内存占用 | < 2GB | 推理时 |

---

## 结论

**完全剔除Python服务和代码是完全可行的**，推荐采用以下技术路线：

1. **推理层**: ONNX Runtime（精度最高，性能优秀）
2. **训练层**: Python训练 → ONNX导出 → Java推理（短期方案）
3. **数据层**: OkHttp + 现有MyBatis/MongoDB/Redis

实施周期预计 **12周**，可以平稳过渡到纯Java架构。

---

*文档版本: 1.0*  
*创建日期: 2026-02-26*  
*作者: mwangli*
