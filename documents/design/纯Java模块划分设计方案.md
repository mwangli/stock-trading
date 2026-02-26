# 纯Java模块划分设计方案（全新项目）

## 文档信息

| 属性 | 内容 |
|------|------|
| 文档编号 | ARCH-2026-002 |
| 文档名称 | 纯Java模块划分设计方案 |
| 版本 | 2.0 |
| 状态 | 初稿 |
| 创建日期 | 2026-02-26 |

---

## 一、技术选型说明

### 1.1 深度学习框架选择

| 框架 | 类型 | 状态 | 说明 |
|------|------|------|------|
| **DJL** | 引擎无关 | 活跃 ⭐⭐⭐⭐⭐ | **本项目采用** |
| DeepLearning4j | 纯Java | 更新放缓 | 不采用 |
| TensorFlow Java | 官方 | 一般 | 不采用 |

**为什么选择DJL？**

- ✅ **AWS维护** - 2025年12月最新版本，活跃度高
- ✅ **引擎无关** - 同一API可切换PyTorch/TensorFlow/MXNet后端
- ✅ **ModelZoo** - 预训练模型库，直接加载
- ✅ **Java原生** - 纯Java体验，无需Python
- ✅ **GPU支持** - 自动检测CUDA加速

### 1.2 技术栈总览

| 模块 | 技术 |
|------|------|
| 深度学习 | DJL 0.28.0 + PyTorch Engine |
| 数值计算 | DJL NDArray |
| HTTP客户端 | OkHttp |
| 数据存储 | MyBatis-Plus + MongoDB + Redis |
| 任务调度 | Spring Quartz |
| Web框架 | Spring Boot 3.2.x |

---

## 二、模块划分方案（新项目-推翻重写）

### 2.1 模块命名

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              目标4模块                                             │
│                                                                                     │
│   ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐   ┌─────────────────┐  │
│   │    databus      │    │   intelligence  │    │   strategy      │   │   executor      │  │
│   │   (数据总线)     │    │    (智能服务)   │    │    (策略服务)   │   │    (执行服务)   │  │
│   └─────────────────┘    └─────────────────┘    └─────────────────┘   └─────────────────┘  │
│                                                                                     │
│         │                       │                       │                   │              │
│    M1-数据采集            M2-情感分析            M4-综合选股            M6-风控管理        │
│                            M3-LSTM预测           M5-决策引擎            M7-交易执行        │
│                                                  M8-模型迭代                                │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责定义

| 模块 | 业务含义 | 包含原模块 | 核心职责 |
|------|---------|----------|---------|
| **databus** | 数据总线 | M1-数据采集 | 数据获取、存储、缓存、分发 |
| **intelligence** | 智能服务 | M2+M3+M8 | AI训练、推理、模型管理 |
| **strategy** | 策略服务 | M4+M5 | 选股策略、交易决策 |
| **executor** | 执行服务 | M6+M7 | 风控检查、交易执行 |

### 2.3 模块名称解释

| 名称 | 含义 | 灵感来源 |
|------|------|---------|
| **databus** | Data Bus - 数据总线 | 企业数据总线架构 |
| **intelligence** | Intelligence - 智能 | AI/ML服务层 |
| **strategy** | Strategy - 策略 | 量化交易策略模块 |
| **executor** | Executor - 执行器 | 执行交易指令 |

---

## 三、模块详细设计

### 3.2 databus 模块（数据总线）

#### 3.2.1 模块定位

```
┌─────────────────────────────────────────────────────────────────┐
│                       databus                                    │
│                      (数据总线)                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │  DataCollector  │  │  DataStorage   │  │  DataCache     │   │
│  │                 │  │                 │  │                 │   │
│  │ • HTTP数据获取  │  │ • MyBatis      │  │ • Redis缓存    │   │
│  │ • 东方财富API  │  │ • MongoDB      │  │ • 实时行情    │   │
│  │ • Baostock API │  │ • 数据清洗     │  │ • 热点数据    │   │
│  │ • 新闻采集      │  │                 │  │                 │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.2.2 包含功能

| 功能 | 说明 |
|------|------|
| 股票列表同步 | 东方财富获取A股股票列表 |
| 历史K线获取 | Baostock/东方财富获取历史数据 |
| 实时行情 | 实时涨跌幅、成交量 |
| 新闻采集 | 财经新闻获取 |
| 数据存储 | MySQL + MongoDB |
| 缓存管理 | Redis热点数据 |

#### 3.2.3 核心类设计

```java
// 模块包结构
com.stock.databus
├── collector/
│   ├── StockCollector       # 股票数据采集
│   ├── NewsCollector       # 新闻采集
│   └── QuoteCollector      # 实时行情
├── client/
│   ├── EastMoneyClient     # 东方财富API
│   ├── BaoStockClient      # Baostock API
│   └── HttpClient          # HTTP封装
├── storage/
│   ├── StockRepository     # MySQL存储
│   ├── PriceRepository     # MongoDB存储
│   └── CacheManager        # Redis缓存
└── scheduled/
    └── DataSyncJob         # 定时同步任务
```

#### 3.2.4 对外接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/data/stock/list` | GET | 获取股票列表 |
| `/api/data/stock/prices` | GET | 获取历史K线 |
| `/api/data/quote/realtime` | GET | 获取实时行情 |
| `/api/data/news` | GET | 获取财经新闻 |

---

### 3.2 intelligence 模块（智能服务）

#### 3.2.1 模块定位

```
┌─────────────────────────────────────────────────────────────────┐
│                     intelligence                                  │
│                     (智能服务)                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                    DJL Core API                          │    │
│  │         Engine agnostic - PyTorch / TensorFlow           │    │
│  └──────────────────────────────────────────────────────────┘    │
│                              │                                   │
│         ┌────────────────────┼────────────────────┐              │
│         ▼                    ▼                    ▼              │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐       │
│  │ Dl4jRuntime │      │ ModelManager│      │Trainer     │       │
│  │             │      │             │      │             │       │
│  │ • 推理引擎  │      │ • 模型加载  │      │ • 模型训练 │       │
│  │ • GPU/CPU   │      │ • 版本管理 │      │ • 评估     │       │
│  │ • NDArray   │      │ • 切换    │      │ • 迭代     │       │
│  └─────────────┘      └─────────────┘      └─────────────┘       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.2.2 包含功能

| 功能 | 说明 |
|------|------|
| 情感分析 | DJL + PyTorch 文本分类 |
| LSTM预测 | DJL + PyTorch 时序预测 |
| 模型训练 | 纯Java端到端训练 |
| 模型管理 | 模型版本、加载、切换 |

#### 3.2.3 核心类设计

```java
// 模块包结构
com.stock.intelligence
├── inference/
│   ├── SentimentInference     # 情感分析推理
│   ├── LstmInference         # LSTM预测推理
│   └── InferenceFactory      # 推理工厂
├── model/
│   ├── DjlModelManager       # DJL模型管理
│   ├── ModelRegistry         # 模型注册
│   └── ModelLoader          # 模型加载器
├── network/
│   ├── SentimentCNN          # 情感分析网络(DJL Block)
│   └── StockLSTM            # LSTM预测网络(DJL Block)
├── trainer/
│   ├── SentimentTrainer     # 情感分析训练
│   ├── LstmTrainer          # LSTM训练
│   └── DatasetIterator      # 数据集迭代器
├── config/
│   └── MlConfig            # ML配置
└── scheduled/
    └── ModelUpdateJob       # 模型更新任务
```

#### 3.2.4 对外接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ai/sentiment/analyze` | POST | 情感分析 |
| `/api/ai/sentiment/batch` | POST | 批量情感分析 |
| `/api/ai/lstm/predict` | POST | LSTM预测 |
| `/api/ai/lstm/batch` | POST | 批量预测 |
| `/api/ai/model/info` | GET | 模型信息 |
| `/api/ai/train/start` | POST | 启动训练 |
| `/api/ai/train/status` | GET | 训练状态 |

#### 3.2.5 Maven依赖 (DJL)

```xml
<!-- DJL 核心API -->
<dependency>
    <groupId>ai.djl</groupId>
    <artifactId>api</artifactId>
    <version>0.28.0</version>
</dependency>

<!-- PyTorch 引擎 (推荐) -->
<dependency>
    <groupId>ai.djl</groupId>
    <artifactId>pytorch-engine</artifactId>
    <version>0.28.0</version>
</dependency>
<dependency>
    <groupId>ai.djl.pytorch</groupId>
    <artifactId>pytorch-model-zoo</artifactId>
    <version>0.28.0</version>
</dependency>

<!-- CUDA GPU支持 (可选，按需添加) -->
<dependency>
    <groupId>ai.djl.pytorch</groupId>
    <artifactId>pytorch-native-cu121</artifactId>
    <version>2.3.0</version>
    <scope>runtime</scope>
</dependency>

<!-- 基础依赖 -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.9</version>
</dependency>
```

#### 3.2.6 DJL 核心代码示例

##### 3.2.6.1 LSTM预测模型定义

```java
// 使用DJL Block定义LSTM网络
public class StockLSTM {
    
    public static Block createLstmBlock(int inputSize, int hiddenSize, int numLayers) {
        return new SequentialBlock()
            // LSTM层
            .add(RecurrentV1.lstm()
                .setStateSize(hiddenSize)
                .setNumLayers(numLayers)
                .setInputSize(inputSize)
                .setDropout(0.2f))
            // 取最后一个输出
            .add(Blocks.batchFlattenBlock())
            // 全连接层
            .add(Linear.builder()
                .setUnits(50)
                .build())
            .add(Activation::relu)
            .add(Linear.builder()
                .setUnits(25)
                .build())
            .add(Activation::relu)
            // 输出层
            .add(Linear.builder()
                .setUnits(1)
                .build());
    }
}
```

##### 3.2.6.2 情感分析模型定义

```java
// 使用DJL Block定义CNN文本分类网络
public class SentimentCNN {
    
    public static Block createTextCNN(int vocabSize, int embeddingDim, int numLabels) {
        return new SequentialBlock()
            // 词嵌入层
            .add(Embedding.builder()
                .setVocabSize(vocabSize)
                .setEmbeddingSize(embeddingDim)
                .build())
            // 卷积层
            .add(Convolution1D.builder()
                .setKernelShape(3)
                .setFilters(64)
                .setActivation(Activation::relu)
                .build())
            .add(Pool1D.globalAvgPool1D())
            // 全连接层
            .add(Linear.builder()
                .setUnits(128)
                .build())
            .add(Activation::relu)
            .add(Linear.builder()
                .setUnits(numLabels)
                .build())
            .add(Softmax::activation);
    }
}
```

##### 3.2.6.3 模型训练

```java
@Component
public class LstmTrainer {
    
    @Autowired
    private ModelManager modelManager;
    
    public void trainLSTM(String stockCode, List<PriceData> trainingData) throws Exception {
        // 1. 创建模型
        Block lstmBlock = StockLSTM.createLstmBlock(5, 100, 2);
        Model model = Model.newInstance("lstm-" + stockCode);
        model.setBlock(lstmBlock);
        
        // 2. 准备数据
        NDManager ndManager = model.getNDManager();
        NDArray features = prepareFeatures(trainingData, ndManager);
        NDArray labels = prepareLabels(trainingData, ndManager);
        
        // 3. 配置训练
        TrainingConfig config = new DefaultTrainingConfig(Loss.l2Loss())
            .setOptimizer(Optimizer.adam()
                .setLearningRate(0.001)
                .build())
            .setDevices(Device.getDevices(1));
        
        // 4. 执行训练
        EasyTrain.fit(model, config, features, labels, 100);
        
        // 5. 保存模型
        model.save(Paths.get("models"), "lstm-" + stockCode);
        
        // 6. 注册模型
        modelManager.registerModel("lstm-" + stockCode, model);
    }
    
    private NDArray prepareFeatures(List<PriceData> data, NDManager manager) {
        // 转换为NDArray [samples, sequence, features]
        float[] array = new float[data.size() * 60 * 5];
        // ... 填充数据
        return manager.create(array, new Shape(data.size(), 60, 5));
    }
}
```

##### 3.2.6.4 模型推理

```java
@Component
public class LstmInference {
    
    private Model model;
    private NDManager ndManager;
    
    @PostConstruct
    public void init() throws Exception {
        // 加载模型
        Path modelPath = Paths.get("models/lstm-stock.pt");
        this.model = Model.newInstance("lstm-stock");
        this.model.load(modelPath, "lstm-stock");
        this.ndManager = model.getNDManager();
    }
    
    public PredictionResult predict(String stockCode, List<PriceData> data) {
        // 1. 准备输入
        NDArray input = prepareInput(data);
        
        // 2. 推理
        try (Predictor<NDList, NDList> predictor = model.newPredictor()) {
            NDList output = predictor.predict(new NDList(input));
            
            // 3. 解析结果
            float predictedPrice = output.get(0).getFloat();
            float currentPrice = data.get(data.size() - 1).getClose();
            float change = predictedPrice - currentPrice;
            
            return PredictionResult.builder()
                .stockCode(stockCode)
                .predictedPrice(predictedPrice)
                .currentPrice(currentPrice)
                .change(change)
                .changePercent(change / currentPrice * 100)
                .direction(change > 0 ? "up" : "down")
                .confidence(0.75)
                .build();
        }
    }
    
    private NDArray prepareInput(List<PriceData> data) {
        // 提取OHLCV特征
        float[] features = new float[data.size() * 5];
        for (int i = 0; i < data.size(); i++) {
            features[i * 5 + 0] = data.get(i).getOpen();
            features[i * 5 + 1] = data.get(i).getHigh();
            features[i * 5 + 2] = data.get(i).getLow();
            features[i * 5 + 3] = data.get(i).getClose();
            features[i * 5 + 4] = data.get(i).getVolume();
        }
        return ndManager.create(features, new Shape(1, data.size(), 5));
    }
}
```

##### 3.2.6.5 情感分析推理

```java
@Component
public class SentimentInference {
    
    private Model model;
    private Tokenizer tokenizer;
    private NDManager ndManager;
    
    @PostConstruct
    public void init() throws Exception {
        // 加载BERT模型
        Criteria<Text, Classifications> criteria = Criteria.builder()
            .setTypes(Text.class, Classifications.class)
            .optModelUrls("djl://ai-djl-benchmark/text_classification/0.0.1/bert")
            .build();
        
        ZooModel<Text, Classifications> bertModel = ModelZoo.loadModel(criteria);
        this.model = bertModel;
        this.ndManager = model.getNDManager();
    }
    
    public SentimentResult analyze(String text) {
        // 分词
        int[] tokenIds = tokenizer.encode(text);
        NDArray input = ndManager.create(tokenIds, new Shape(1, tokenIds.length));
        
        // 推理
        try (Predictor<NDList, NDList> predictor = model.newPredictor()) {
            NDList output = predictor.predict(new NDList(input));
            
            // 解析结果
            float[] probs = output.get(0).toFloatArray();
            int predictedClass = argmax(probs);
            
            String label = predictedClass == 0 ? "positive" : 
                          predictedClass == 1 ? "neutral" : "negative";
            float score = probs[predictedClass];
            
            return SentimentResult.builder()
                .label(label)
                .score(score)
                .confidence(score)
                .positiveRatio(probs[0])
                .neutralRatio(probs[1])
                .negativeRatio(probs[2])
                .build();
        }
    }
    
    private int argmax(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}
```

##### 3.2.6.6 模型管理

```java
@Component
public class DjlModelManager {
    
    private final Map<String, Model> modelCache = new ConcurrentHashMap<>();
    private final Map<String, ModelInfo> modelInfoMap = new ConcurrentHashMap<>();
    
    public void registerModel(String name, Model model) {
        modelCache.put(name, model);
        modelInfoMap.put(name, ModelInfo.builder()
            .name(name)
            .loadedTime(LocalDateTime.now())
            .status("ACTIVE")
            .build());
    }
    
    public Model getModel(String name) {
        return modelCache.get(name);
    }
    
    public void unloadModel(String name) {
        Model model = modelCache.remove(name);
        if (model != null) {
            model.close();
        }
        modelInfoMap.remove(name);
    }
    
    public List<ModelInfo> listModels() {
        return new ArrayList<>(modelInfoMap.values());
    }
}
```

---

### 3.3 stock-trading 模块（核心业务）

#### 3.3.1 模块定位

```
┌─────────────────────────────────────────────────────────────────┐
│                       stock-trading                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │   StockSelector │  │ DecisionEngine │  │ ModelTraining  │   │
│  │                 │  │                 │  │                 │   │
│  │ • 双因子选股    │  │ • 信号生成      │  │ • 训练任务     │   │
│  │ • 因子计算     │  │ • 策略执行     │  │ • 模型评估     │   │
│  │ • 排名算法     │  │ • 仓位管理     │  │ • 迭代优化     │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │                   Business Services                       │    │
│  │  • SelectService (选股服务)                               │    │
│  │  • DecisionService (决策服务)                             │    │
│  │  • ModelManageService (模型管理)                          │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.3.2 包含功能

| 功能 | 原模块 | 说明 |
|------|--------|------|
| 综合选股 | M4 | 双因子(LSTM+情感)选股 |
| 决策引擎 | M5 | 交易信号生成 |
| 模型迭代 | M8 | 模型训练、评估、更新 |

#### 3.3.3 核心类设计

```java
// 模块包结构
online.mwang.stock.core
├── selector/
│   ├── StockSelector          # 选股器
│   ├── FactorCalculator      # 因子计算
│   └── RankingService        # 排名服务
├── decision/
│   ├── DecisionEngine        # 决策引擎
│   ├── SignalGenerator       # 信号生成
│   └── StrategyManager       # 策略管理
├── training/
│   ├── ModelTrainer          # 模型训练
│   ├── ModelEvaluator        # 模型评估
│   └── TrainingScheduler    # 训练调度
├── service/
│   ├── SelectService         # 选股服务
│   ├── DecisionService       # 决策服务
│   └── ModelManageService   # 模型管理服务
└── entity/
    ├── StockSentiment        # 情感实体
    ├── StockPrediction      # 预测实体
    └── TradingSignal        # 交易信号
```

#### 3.3.4 对外接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/core/select` | GET | 获取推荐股票 |
| `/api/core/select/rank` | GET | 股票排名 |
| `/api/core/decision` | POST | 生成交易决策 |
| `/api/core/signal` | GET | 获取交易信号 |
| `/api/core/training/start` | POST | 启动训练 |
| `/api/core/training/status` | GET | 训练状态 |

#### 3.3.5 选股算法

```java
/**
 * 综合选股公式
 * Score = LSTM_score × 0.6 + Sentiment_score × 0.4
 * 
 * 其中：
 * - LSTM_score: 基于LSTM预测的涨跌概率
 * - Sentiment_score: 基于情感分析的得分
 */
public class StockSelector {
    
    public List<StockRecommendation> select(int limit) {
        // 1. 获取所有股票的LSTM预测
        List<Prediction> predictions = lstmInference.getAllPredictions();
        
        // 2. 获取所有股票的情感得分
        List<Sentiment> sentiments = sentimentService.getAllSentiments();
        
        // 3. 计算综合得分
        List<ScoredStock> scoredStocks = calculateScores(predictions, sentiments);
        
        // 4. 排序返回Top N
        return scoredStocks.stream()
            .sorted(Comparator.comparing(ScoredStock::getScore).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    private double calculateScore(Prediction pred, Sentiment sent) {
        double lstmScore = pred.getChangePercent() / 10.0;  // 归一化
        double sentimentScore = sent.getScore();
        
        // Score = LSTM×0.6 + Sentiment×0.4
        return lstmScore * 0.6 + sentimentScore * 0.4;
    }
}
```

---

### 3.4 stock-execution 模块（交易执行）

#### 3.4.1 模块定位

```
┌─────────────────────────────────────────────────────────────────┐
│                      stock-execution                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │
│  │  RiskControl   │  │  TradeExecutor │  │  PositionMgr   │   │
│  │                 │  │                 │  │                 │   │
│  │ • 止损检查      │  │ • 买入执行      │  │ • 持仓管理      │   │
│  │ • 熔断检查      │  │ • 卖出执行      │  │ • 仓位计算      │   │
│  │ • 仓位控制      │  │ • 委托管理      │  │ • 盈亏统计      │   │
│  │ • 风险预警      │  │ • 成交确认      │  │ • 历史记录      │   │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘   │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │                    Broker Adapters                         │    │
│  │  • CiticAdapter (中信证券)                                │    │
│  │  • ExtBrokerAdapter (扩展券商)                            │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.4.2 包含功能

| 功能 | 原模块 | 说明 |
|------|--------|------|
| 风控管理 | M6 | 风险控制检查 |
| 交易执行 | M7 | 券商API交易 |

#### 3.4.3 核心类设计

```java
// 模块包结构
online.mwang.stock.trade
├── risk/
│   ├── RiskController        # 风控检查
│   ├── StopLossChecker       # 止损检查
│   ├── PositionChecker       # 仓位检查
│   └── RiskAlertService      # 风险预警
├── execution/
│   ├── TradeExecutor         # 交易执行
│   ├── OrderManager          # 委托管理
│   └── TradeConfirm          # 成交确认
├── position/
│   ├── PositionManager       # 持仓管理
│   ├── PositionRepository    # 持仓仓储
│   └── ProfitCalculator      # 盈亏计算
├── broker/
│   ├── BrokerAdapter         # 券商适配器接口
│   ├── CiticAdapter          # 中信证券适配器
│   └── BrokerFactory         # 券商工厂
└── scheduled/
    ├── MorningTradeJob       # 早盘交易任务
    └── AfternoonTradeJob    # 尾盘交易任务
```

#### 3.4.4 对外接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/trade/buy` | POST | 买入下单 |
| `/api/trade/sell` | POST | 卖出下单 |
| `/api/trade/order/{id}` | GET | 委托查询 |
| `/api/trade/position` | GET | 持仓查询 |
| `/api/trade/risk/check` | POST | 风控检查 |
| `/api/trade/account` | GET | 账户信息 |

#### 3.4.5 风控规则

```java
@Component
public class RiskController {
    
    /**
     * 风控检查规则
     * 1. 日亏损不超过3%
     * 2. 月亏损不超过10%
     * 3. 单只股票仓位不超过30%
     * 4. 总仓位不超过80%
     * 5. 止损线检查
     */
    public RiskCheckResult checkBeforeBuy(TradeRequest request) {
        List<String> violations = new ArrayList<>();
        
        // 规则1: 日亏损检查
        if (dailyLossPercent > 3.0) {
            violations.add("当日亏损已超过3%，禁止买入");
        }
        
        // 规则2: 月亏损检查
        if (monthlyLossPercent > 10.0) {
            violations.add("当月亏损已超过10%，禁止买入");
        }
        
        // 规则3: 单股仓位检查
        double positionPercent = calculatePositionPercent(request.getStockCode());
        if (positionPercent > 30.0) {
            violations.add("单只股票仓位超过30%");
        }
        
        // 规则4: 总仓位检查
        double totalPosition = calculateTotalPosition();
        if (totalPosition > 80.0) {
            violations.add("总仓位超过80%");
        }
        
        return RiskCheckResult.builder()
            .passed(violations.isEmpty())
            .violations(violations)
            .build();
    }
}
```

---

## 四、模块依赖关系

### 4.1 依赖图

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              模块依赖关系                                           │
│                                                                                     │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │
│  │ databus  │────▶│   intelligence  │────▶│ stock-trading  │────▶│stock-execution  │       │
│  │  (数据层)   │     │   (AI层)     │     │  (业务层)   │     │  (交易层)   │       │
│  └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘       │
│       │                   │                   │                   ▲                │
│       │                   │                   │                   │                │
│       │                   │                   │                   │                │
│       │                   │                   │                   │                │
│  ┌────┴────┐        ┌─────┴─────┐      ┌─────┴─────┐       ┌─────┴─────┐        │
│  │MySQL    │        │  ONNX     │      │  intelligence │       │  券商API  │        │
│  │MongoDB  │        │  Models   │      │           │       │           │        │
│  │Redis    │        │           │      │           │       │           │        │
│  └─────────┘        └───────────┘      └───────────┘       └───────────┘        │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 依赖说明

| 上游模块 | 下游模块 | 依赖内容 |
|---------|---------|---------|
| **databus** | intelligence | 提供原始数据(新闻、历史K线) |
| **intelligence** | stock-trading | 提供情感得分、预测价格 |
| **stock-trading** | stock-execution | 提供交易信号 |
| **stock-execution** | stock-trading | 反馈交易结果(用于模型迭代) |

### 4.3 数据流转

```
每日执行流程:

1. databus (09:00)
   └── 同步股票列表、历史数据

2. databus (09:30)
   └── 采集实时行情 → Redis缓存

3. intelligence (15:30)
   └── 读取MongoDB新闻 → 情感分析 → 写入MySQL

4. intelligence (16:00)
   └── 读取MongoDB K线 → LSTM预测 → 写入MySQL

5. stock-trading (16:30)
   └── 读取MySQL(情感+预测) → 综合选股 → 生成信号

6. stock-execution (09:35)
   └── 读取信号 → 风控检查 → 执行交易

7. stock-trading (收盘后)
   └── 收集交易数据 → 模型迭代评估
```

---

## 五、项目结构设计

### 5.1 Maven多模块项目

```
stock-trading/
├── pom.xml                    # 父POM
│
├── stock-common/              # 公共模块
│   ├── pom.xml
│   └── src/main/java/online/mwang/stock/common/
│       ├── constant/          # 常量定义
│       ├── dto/               # 通用DTO
│       ├── enum/              # 枚举
│       ├── exception/         # 异常定义
│       └── utils/             # 工具类
│
├── databus/                # 数据采集模块
│   ├── pom.xml
│   └── src/main/java/online/mwang/stock/data/
│       ├── collector/         # 数据采集
│       ├── client/            # HTTP客户端
│       ├── repository/        # 数据仓储
│       ├── cache/             # 缓存管理
│       └── scheduled/          # 定时任务
│
├── intelligence/                  # AI推理模块
│   ├── pom.xml
│   └── src/main/java/online/mwang/stock/ai/
│       ├── inference/         # 推理服务
│       ├── model/             # 模型管理
│       ├── tokenizer/         # 分词器
│       ├── config/            # 配置
│       └── scheduled/         # 任务
│
├── stock-trading/                # 核心业务模块
│   ├── pom.xml
│   └── src/main/java/online/mwang/stock/core/
│       ├── selector/          # 选股器
│       ├── decision/         # 决策引擎
│       ├── training/         # 训练管理
│       ├── service/          # 业务服务
│       └── entity/           # 业务实体
│
├── stock-execution/              # 交易执行模块
│   ├── pom.xml
│   └── src/main/java/online/mwang/stock/trade/
│       ├── risk/             # 风控
│       ├── execution/        # 执行
│       ├── position/         # 持仓
│       ├── broker/           # 券商适配
│       └── scheduled/        # 任务
│
└── stock-web/                # Web启动模块
    ├── pom.xml
    └── src/main/java/online/mwang/stock/web/
        ├── controller/       # 控制器
        ├── config/          # 配置
        ├── interceptor/     # 拦截器
        └── Security.java   # 安全配置
```

### 5.2 父POM配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>online.mwang</groupId>
    <artifactId>stock-trading</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>Stock Trading System</name>
    
    <modules>
        <module>stock-common</module>
        <module>databus</module>
        <module>intelligence</module>
        <module>stock-trading</module>
        <module>stock-execution</module>
        <module>stock-web</module>
    </modules>
    
    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.2.2</spring-boot.version>
        <onnxruntime.version>1.16.3</onnxruntime.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- 内部模块 -->
            <dependency>
                <groupId>online.mwang</groupId>
                <artifactId>stock-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <!-- ... 其他依赖 -->
        </dependencies>
    </dependencyManagement>
</project>
```

---

## 六、模块依赖配置

### 6.1 databus pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>online.mwang</groupId>
        <artifactId>stock-trading</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>databus</artifactId>
    <name>Stock Data Module</name>
    
    <dependencies>
        <!-- 内部模块 -->
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>stock-common</artifactId>
        </dependency>
        
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        
        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        
        <!-- HTTP Client -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </dependency>
        
        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 6.2 intelligence pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>online.mwang</groupId>
        <artifactId>stock-trading</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>intelligence</artifactId>
    <name>Stock AI Module</name>
    
    <dependencies>
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>stock-common</artifactId>
        </dependency>
        
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>databus</artifactId>
        </dependency>
        
        <!-- ONNX Runtime -->
        <dependency>
            <groupId>ai.onnxruntime</groupId>
            <artifactId>onnxruntime</artifactId>
            <version>1.16.3</version>
        </dependency>
        
        <!-- ND4J -->
        <dependency>
            <groupId>org.nd4j</groupId>
            <artifactId>nd4j-native-platform</artifactId>
            <version>1.0.0-M2.1</version>
        </dependency>
        
        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 6.3 stock-trading pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>online.mwang</groupId>
        <artifactId>stock-trading</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>stock-trading</artifactId>
    <name>Stock Core Module</name>
    
    <dependencies>
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>stock-common</artifactId>
        </dependency>
        
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>intelligence</artifactId>
        </dependency>
        
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 6.4 stock-execution pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>online.mwang</groupId>
        <artifactId>stock-trading</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>stock-execution</artifactId>
    <name>Stock Trade Module</name>
    
    <dependencies>
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>stock-common</artifactId>
        </dependency>
        
        <dependency>
            <groupId>online.mwang</groupId>
            <artifactId>stock-trading</artifactId>
        </dependency>
        
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 七、接口路由设计

### 7.1 API路由分组

```
/api/data/*         → databus 模块
/api/ai/*           → intelligence 模块
/api/core/*         → stock-trading 模块
/api/trade/*        → stock-execution 模块
```

### 7.2 完整接口清单

#### databus 模块

| 路由 | 方法 | 说明 |
|------|------|------|
| `/api/data/stock/list` | GET | 获取股票列表 |
| `/api/data/stock/info/{code}` | GET | 股票基本信息 |
| `/api/data/stock/prices` | GET | 历史K线数据 |
| `/api/data/quote/realtime` | GET | 实时行情 |
| `/api/data/news` | GET | 财经新闻 |
| `/api/data/sync/stock-list` | POST | 手动同步股票 |
| `/api/data/sync/historical` | POST | 手动同步历史 |

#### intelligence 模块

| 路由 | 方法 | 说明 |
|------|------|------|
| `/api/ai/sentiment/analyze` | POST | 情感分析 |
| `/api/ai/sentiment/batch` | POST | 批量情感分析 |
| `/api/ai/sentiment/stock/{code}` | GET | 股票情感得分 |
| `/api/ai/lstm/predict` | POST | LSTM预测 |
| `/api/ai/lstm/batch` | POST | 批量预测 |
| `/api/ai/model/info` | GET | 模型信息 |
| `/api/ai/model/update` | POST | 更新模型 |

#### stock-trading 模块

| 路由 | 方法 | 说明 |
|------|------|------|
| `/api/core/select` | GET | 获取推荐股票 |
| `/api/core/select/rank` | GET | 股票排名 |
| `/api/core/decision` | POST | 生成决策 |
| `/api/core/signal` | GET | 当前交易信号 |
| `/api/core/training/start` | POST | 启动训练 |
| `/api/core/training/stop` | POST | 停止训练 |
| `/api/core/training/status` | GET | 训练状态 |
| `/api/core/performance` | GET | 模型表现 |

#### stock-execution 模块

| 路由 | 方法 | 说明 |
|------|------|------|
| `/api/trade/buy` | POST | 买入下单 |
| `/api/trade/sell` | POST | 卖出下单 |
| `/api/trade/cancel/{orderId}` | POST | 撤单 |
| `/api/trade/order/{orderId}` | GET | 委托查询 |
| `/api/trade/orders` | GET | 委托列表 |
| `/api/trade/position` | GET | 持仓查询 |
| `/api/trade/account` | GET | 账户信息 |
| `/api/trade/risk/check` | POST | 风控检查 |
| `/api/trade/risk/status` | GET | 风控状态 |

---

## 八、定时任务配置

### 8.1 任务分布

| 模块 | 任务 | 时间 | 功能 |
|------|------|------|------|
| **databus** | StockListSyncJob | 09:00 | 同步股票列表 |
| **databus** | HistoricalSyncJob | 09:30 | 同步历史数据 |
| **databus** | RealtimeQuoteJob | 每分钟 | 实时行情 |
| **databus** | NewsCollectJob | 15:30 | 新闻采集 |
| **intelligence** | SentimentJob | 16:00 | 情感分析 |
| **intelligence** | PredictionJob | 16:30 | LSTM预测 |
| **stock-trading** | SelectionJob | 17:00 | 综合选股 |
| **stock-execution** | MorningTradeJob | 09:35 | 早盘交易 |
| **stock-execution** | AfternoonTradeJob | 14:50 | 尾盘交易 |
| **stock-trading** | ModelEvalJob | 收盘后 | 模型评估 |

### 8.2 配置示例

```java
// databus 模块定时任务
@Configuration
public class DataSchedulerConfig {
    
    @Scheduled(cron = "0 0 9 * * 1-5")
    public void syncStockList() {
        // 同步股票列表
    }
    
    @Scheduled(cron = "0 0 9 * * 1-5")
    public void syncHistoricalData() {
        // 同步历史K线
    }
    
    @Scheduled(cron = "0 */1 * * * *")
    public void syncRealtimeQuotes() {
        // 每分钟同步实时行情
    }
}

// intelligence 模块定时任务
@Configuration
public class AiSchedulerConfig {
    
    @Scheduled(cron = "0 0 16 * * 1-5")
    public void runSentimentAnalysis() {
        // 情感分析
    }
    
    @Scheduled(cron = "0 30 16 * * 1-5")
    public void runPrediction() {
        // LSTM预测
    }
}
```

---

## 九、总结

### 9.1 模块划分总览

| 模块 | 原模块 | 核心功能 | 技术栈 |
|------|--------|---------|--------|
| **databus** | M1-数据采集 | 数据获取、存储、缓存 | OkHttp + MyBatis + Redis |
| **intelligence** | M2-情感分析, M3-LSTM预测 | AI模型推理 | ONNX Runtime |
| **stock-trading** | M4-综合选股, M5-决策引擎, M8-模型迭代 | 选股、决策、训练 | 业务逻辑 |
| **stock-execution** | M6-风控管理, M7-交易执行 | 交易执行、风控 | 券商API |

### 9.2 优势

1. **低耦合**：模块间通过接口通信
2. **高内聚**：相关功能集中在同一模块
3. **可维护**：便于独立开发和测试
4. **可部署**：可按需独立部署
5. **纯Java**：完全剔除Python依赖

### 9.3 实施顺序

```
Phase 1: stock-common + databus
    └── 先搭建数据层
    
Phase 2: intelligence
    └── 集成ONNX推理

Phase 3: stock-trading + stock-execution
    └── 开发业务逻辑和交易

Phase 4: stock-web
    └── 整合所有模块，提供Web接口
```

---

*文档版本: 1.0*  
*创建日期: 2026-02-26*  
*作者: mwangli*
