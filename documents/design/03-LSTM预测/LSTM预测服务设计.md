# LSTM预测模块设计

## 1. 文档信息

| 属性 | 内容 |
|------|------|
| 模块ID | MOD-003 |
| 模块名称 | LSTM预测 |
| 版本 | 1.0 |
| 状态 | 已完成 |
| 创建日期 | 2026-02-17 |
| 最后更新 | 2026-02-17 |
| 作者 | mwangli |

## 2. 概述

### 2.1 模块定位

LSTM预测模块是股票交易系统的核心技术模块，负责使用长短期记忆网络（LSTM）对股票价格进行预测，为综合选股模块提供技术因子。

### 2.1.1 什么是LSTM

LSTM（Long Short-Term Memory，长短期记忆网络）是一种特殊的循环神经网络（RNN），专为处理时间序列数据设计。与传统RNN相比，LSTM通过引入"门控"机制（遗忘门、输入门、输出门），能够有效解决长序列训练中的梯度消失问题，捕捉数据中的长期依赖关系。

LSTM的核心结构：

```
遗忘门: f_t = σ(W_f · [h_{t-1}, x_t] + b_f)
输入门: i_t = σ(W_i · [h_{t-1}, x_t] + b_i)
         C~_t = tanh(W_c · [h_{t-1}, x_t] + b_c)
细胞状态: C_t = f_t * C_{t-1} + i_t * C~_t
输出门: o_t = σ(W_o · [h_{t-1}, x_t] + b_o)
         h_t = o_t * tanh(C_t)
```

### 2.1.2 股票预测为什么选择LSTM

股票价格是典型的时间序列数据，具有时序依赖性和非线性特征：

| 特点 | 传统方法 | LSTM |
|------|---------|------|
| 时序依赖 | 需手动特征工程 | 自动学习 |
| 非线性建模 | 能力有限 | 能力强 |
| 长期依赖 | 效果差 | 效果好 |
| 多变量融合 | 困难 | 容易 |

### 2.1.3 本系统LSTM模型架构

```
输入: (batch_size, 60, 5)  ← 60天 × 5个OHLCV特征
         │
         ▼
┌─────────────────────────────────────┐
│  LSTM(50, return_sequences=True)   │  ← 第一层LSTM
│  Dropout(0.2)                      │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  LSTM(50, return_sequences=False)  │  ← 第二层LSTM
│  Dropout(0.2)                      │
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Dense(25, activation='relu')      │  ← 全连接层
└─────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Dense(1)                           │  ← 输出层
└─────────────────────────────────────┘
         │
         ▼
输出: (batch_size, 1)  ← 次日收盘价预测
```

### 2.2 功能摘要

- 预测下一交易日收盘价
- 批量预测多只股票
- 提供预测置信度和方向判断

### 2.3 设计原则

> **只预测下一个交易日的收盘价，以确保最高预测准确率。**

- 多日预测误差会累积放大，导致准确率急剧下降
- 股票市场的短期波动更适合逐日预测
- T+1交易策略只需要次日价格信息

### 2.4 上下游依赖

| 上游模块 | 接口 | 数据 |
|----------|------|------|
| 数据采集 | getStockPrices() | 历史K线数据 |

| 下游模块 | 接口 | 数据 |
|----------|------|------|
| 综合选股 | predict() | 预测价格和方向 |
| 决策引擎 | predict() | 技术因子 |

## 3. 功能需求

### 3.1 核心功能列表

| 功能ID | 功能名称 | 功能描述 | 优先级 |
|--------|----------|----------|--------|
| F-001 | 次日价格预测 | 预测单只股票下一交易日收盘价 | P0 |
| F-002 | 批量价格预测 | 批量预测多只股票次日价格 | P0 |
| F-003 | 模型信息查询 | 获取模型版本和状态 | P1 |

### 3.2 功能详情

#### F-001: 次日价格预测

**功能描述**: 基于60天历史K线数据，预测下一交易日收盘价。

**输入**:
- stockCode: 股票代码
- data: 历史K线数据列表（需至少60条）

**处理逻辑**:
1. 数据校验（数量、完整性）
2. 特征提取（OHLCV）
3. 数据归一化
4. 序列生成
5. 模型推理
6. 结果反归一化

**输出**:
```json
{
  "stock_code": "600519",
  "predicted_price": 1850.5,
  "current_price": 1820.0,
  "change": 30.5,
  "change_percent": 1.68,
  "direction": "up",
  "confidence": 0.75,
  "is_trained": true
}
```

**业务规则**:
- 需要至少60条历史数据
- direction: up/down/neutral
- confidence范围: 0-1

#### F-002: 批量价格预测

**功能描述**: 批量预测多只股票次日价格。

**输入**:
- stockCodes: 股票代码列表
- dataMap: 股票代码到历史数据的映射

**输出**: 预测结果列表

**业务规则**:
- 单次批量最大50只股票
- 返回结果顺序与输入一致

## 4. 非功能需求

### 4.1 性能要求

| 指标 | 要求 | 说明 |
|------|------|------|
| 单股预测 | < 1s | 含数据处理和推理 |
| 批量预测(50只) | < 30s | 并行推理 |
| 模型加载 | < 10s | 首次调用 |

### 4.2 可用性要求

- 模型加载失败使用fallback模式
- GPU不可用时降级到CPU
- 服务SLA: 99.5%

### 4.3 资源要求

| 资源 | 占用 |
|------|------|
| GPU显存 | ~500MB |
| 内存 | ~1GB |
| 模型文件 | ~2MB |
| 磁盘 | ~100MB |

## 5. 总体设计

## 5. 总体设计

### 5.1 技术选型

#### 5.1.1 深度学习框架对比

| 维度 | TensorFlow/Keras | PyTorch | 结论 |
|------|-----------------|---------|------|
| **NLP/Transformer** | 较弱 | ✅ 碾压级优势 | PyTorch胜 |
| **金融领域模型** | ❌ 无官方支持 | ✅ FinBERT | PyTorch胜 |
| **LSTM支持** | 成熟 | 成熟 | 持平 |
| **研究生态** | 较少 | 90%+论文 | PyTorch胜 |
| **部署** | TFServing | TorchServe | TensorFlow胜 |
| **内存占用** | 较高 | 较低 | PyTorch胜 |
| **Windows兼容** | 一般 | 更好 | PyTorch胜 |

#### 5.1.2 为什么选择 PyTorch

1. **统一技术栈**：
   - 情感分析模块已使用 PyTorch + FinBERT
   - 避免同时维护两个大框架

2. **FinBERT 金融专用**：
   - TensorFlow 生态没有金融领域 BERT 模型
   - FinBERT 专门在金融文本上预训练，准确率高 15-20%

3. **未来扩展性**：
   - 如果要做新闻摘要、问答等NLP任务，PyTorch生态更丰富
   - 大多数新论文、模型都以 PyTorch 为主

#### 5.1.3 技术架构

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| 深度学习框架 | **PyTorch** | 2.x |
| 数值计算 | NumPy | 1.x |
| 数据处理 | Pandas | 2.x |
| 数据预处理 | Scikit-learn | 1.x |
| Web框架 | FastAPI | 0.109.x |

### 5.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      FastAPI (py-service)                        │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    API Layer                             │   │
│  │   POST /api/lstm/predict          (次日价格预测)        │   │
│  │   POST /api/lstm/predict/batch   (批量预测)            │   │
│  │   GET  /api/lstm/model/info       (模型信息)           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                  │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │                  Service Layer                        │       │
│  │  ┌─────────────────────────────────────────────┐    │       │
│  │  │           LSTMService                         │    │       │
│  │  │  - predict()           单股票次日预测        │    │       │
│  │  │  - predict_batch()    批量次日预测          │    │       │
│  │  │  - get_model_info()  模型信息               │    │       │
│  │  └─────────────────────────────────────────────┘    │       │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                  │
│  ┌──────────────────────────▼──────────────────────────┐       │
│  │                   Model Layer                         │       │
│  │  ┌─────────────────────────────────────────────┐    │       │
│  │  │        LSTM Neural Network                   │    │       │
│  │  │  Input:  [60, 5]  (60天 × 5特征)           │    │       │
│  │  │  Layers: LSTM(50) → Dropout → LSTM(50) → Dense    │       │
│  │  │  Output: [1]  (次日收盘价预测)              │    │       │
│  │  └─────────────────────────────────────────────┘    │       │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Backend                         │
│  ┌─────────────────┐  ┌─────────────────┐                      │
│  │  LSTMClient    │  │ LSTMPredictService                     │
│  │   (Feign)     │  │   (业务服务)    │                      │
│  └─────────────────┘  └─────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 部署架构

- 部署方式: Python微服务独立部署
- 端口: 8001
- 依赖: PyTorch, NumPy, Pandas
- 调用方式: HTTP REST API

## 6. 详细设计

### 6.1 模型设计

#### 6.1.1 网络结构

```
┌─────────────────────────────────────────────────────────────┐
│                    LSTM Model Architecture                   │
├─────────────────────────────────────────────────────────────┤
│  Input: (batch_size, 60, 5)    # 60天历史, 5个特征       │
│         │                                                │
│         ▼                                                │
│  ┌─────────────────────────────────────────────────┐      │
│  │  LSTM(50, return_sequences=True)               │      │
│  │  Dropout(0.2)                                  │      │
│  └─────────────────────────────────────────────────┘      │
│         │                                                │
│         ▼                                                │
│  ┌─────────────────────────────────────────────────┐      │
│  │  LSTM(50, return_sequences=False)             │      │
│  │  Dropout(0.2)                                  │      │
│  └─────────────────────────────────────────────────┘      │
│         │                                                │
│         ▼                                                │
│  ┌─────────────────────────────────────────────────┐      │
│  │  Dense(25, activation='relu')                  │      │
│  └─────────────────────────────────────────────────┘      │
│         │                                                │
│         ▼                                                │
│  ┌─────────────────────────────────────────────────┐      │
│  │  Dense(1)                                       │      │
│  └─────────────────────────────────────────────────┘      │
│         │                                                │
│         ▼                                                │
│  Output: (batch_size, 1)       # 次日收盘价预测          │
└─────────────────────────────────────────────────────────────┘
```

#### 6.1.2 输入特征

| 序号 | 特征 | 说明 |
|------|------|------|
| 1 | Open | 开盘价 |
| 2 | High | 最高价 |
| 3 | Low | 最低价 |
| 4 | Close | 收盘价 |
| 5 | Volume | 成交量 |

### 6.2 数据预处理

1. **归一化**: 使用MinMaxScaler将数据缩放到(0,1)范围
2. **序列生成**: 将60天数据生成一个输入序列
3. **反归一化**: 预测结果逆变换为实际价格

### 6.3 服务接口

```python
class LSTMService:
    """LSTM-based stock prediction service"""

    def __init__(self):
        self.sequence_length = 60      # 输入序列长度
        self.feature_dim = 5             # 特征维度 (OHLCV)
        self._model = None
        self._scaler = MinMaxScaler()
        self._is_trained = False

    def predict(self, stock_code: str, data: List[Dict]) -> Dict:
        """预测次日价格"""
        pass

    def predict_batch(self, stock_codes: List[str], data_map: Dict[str, List[Dict]]) -> List[Dict]:
        """批量预测多只股票次日价格"""
        pass

    def get_model_info(self) -> Dict:
        """获取模型信息"""
        pass
```

### 6.4 数据模型设计

#### 6.4.1 数据表清单

| 表名 | 说明 | 存储 |
|------|------|------|
| model_info | LSTM模型信息表 | MySQL |

#### 6.4.2 SQL建表语句

**model_info 表 (LSTM模型信息)**

```sql
CREATE TABLE `model_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(10) NOT NULL COMMENT '股票代码',
  `name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `train_period` varchar(50) DEFAULT NULL COMMENT '训练周期(如: 2020-01-01~2024-01-01)',
  `train_times` int(11) DEFAULT '0' COMMENT '训练次数',
  `test_deviation` double DEFAULT NULL COMMENT '测试偏差率(%)',
  `score` double DEFAULT NULL COMMENT '模型评分',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '模型状态(ACTIVE-活跃,INACTIVE-停用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSTM模型信息表';
```

#### 6.4.3 表结构说明

**model_info 表字段说明**:

| 字段名 | 类型 | 长度 | 说明 | 可空 |
|--------|------|------|------|------|
| id | BIGINT | 20 | 主键ID，自增 | 否 |
| code | VARCHAR | 10 | 股票代码，唯一索引 | 否 |
| name | VARCHAR | 50 | 股票名称 | 是 |
| train_period | VARCHAR | 50 | 训练数据周期范围 | 是 |
| train_times | INT | 11 | 模型训练次数 | 是 |
| test_deviation | DOUBLE | - | 测试集偏差率(%)，越小越好 | 是 |
| score | DOUBLE | - | 模型综合评分(0-100) | 是 |
| status | VARCHAR | 20 | 模型状态(ACTIVE/INACTIVE) | 是 |
| create_time | DATETIME | - | 记录创建时间 | 是 |
| update_time | DATETIME | - | 记录更新时间 | 是 |

**索引设计**:
- 主键索引: `id`
- 唯一索引: `code` (每只股票对应一个模型)
- 普通索引: `status` (按状态查询), `score` (按评分排序)

**模型评分算法**:
```
score = (1 - test_deviation) * 100
```
评分越高表示模型预测偏差越小，质量越好。

**模型状态说明**:
- `ACTIVE`: 模型活跃，可用于预测
- `INACTIVE`: 模型停用，不参与预测

#### 6.4.4 Java实体类定义

```java
@Data
@TableName(value = "model_info", autoResultMap = true)
public class ModelInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;            // 股票代码
    private String name;            // 股票名称
    private String trainPeriod;     // 训练周期
    private Integer trainTimes;     // 训练次数
    private Double testDeviation;   // 测试偏差率
    private Double score;           // 模型评分
    private String status;          // 模型状态
    private Date createTime;
    private Date updateTime;

    // 动态排序方法
    public static SFunction<ModelInfo, Object> getOrder(String key) {
        // 根据字段名返回排序函数
    }
}
```

### 6.5 接口设计

#### 6.5.1 接口列表

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| /api/lstm/predict | POST | 次日价格预测 |
| /api/lstm/predict/batch | POST | 批量预测 |
| /api/lstm/model/info | GET | 模型信息 |

#### 6.4.2 接口详情

**POST /api/lstm/predict**

请求:
```json
{
  "stock_code": "600519",
  "data": [
    {"date": "2024-01-01", "open": 10.5, "high": 10.8, "low": 10.4, "close": 10.6, "volume": 1000000},
    ...
  ]
}
```

响应:
```json
{
  "stock_code": "600519",
  "predicted_price": 1850.5,
  "current_price": 1820.0,
  "change": 30.5,
  "change_percent": 1.68,
  "direction": "up",
  "confidence": 0.75,
  "is_trained": true
}
```

### 6.5 预测流程

```
输入: 股票代码 + 60天OHLCV数据
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 1: 数据校验                                           │
│  - 检查数据量是否 >= 60条                                   │
│  - 检查数据完整性                                           │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 2: 特征提取                                           │
│  - 提取 OHLCV 五个特征                                     │
│  - 转换为 numpy 数组                                        │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 3: 数据归一化                                         │
│  - 使用 MinMaxScaler 归一化                                 │
│  - 保存 scaler 用于逆变换                                   │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 4: 序列生成                                           │
│  - 创建时间序列窗口 (60天)                                  │
│  - 形状: [samples, 60, 5]                                  │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 5: 模型推理                                           │
│  - LSTM 模型预测                                             │
│  - 输出次日收盘价                                            │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│  Step 6: 结果处理                                           │
│  - 逆归一化得到实际价格                                      │
│  - 计算涨跌额和涨跌幅                                        │
│  - 确定预测方向                                              │
└─────────────────────────────────────────────────────────────┘
            │
            ▼
输出: 预测结果 (predicted_price, change, direction, confidence)
```

### 6.6 与后端集成

```java
@FeignClient(name = "lstm-service", url = "${ai.service.url}")
public interface LSTMPredictClient {
    
    @PostMapping("/api/lstm/predict")
    PredictionResponse predict(@Body PredictionRequest request);
    
    @PostMapping("/api/lstm/predict/batch")
    List<PredictionResponse> predictBatch(@Body BatchPredictionRequest request);
    
    @GetMapping("/api/lstm/model/info")
    ModelInfoResponse getModelInfo();
}
```

### 6.7 配置设计

```yaml
# LSTM预测服务配置
lstm:
  model:
    path: /models/lstm_stock.h5
    sequence_length: 60
    feature_dim: 5
    device: auto  # auto/cpu/cuda
  
  api:
    batch_size: 50
    timeout: 30000
  
  fallback:
    # 模型不存在时的默认值
    confidence: 0.5
    is_trained: false
```

## 7. 错误处理

### 7.1 错误码定义

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| LSTM_001 | 数据不足 | 需要至少60条数据 |
| LSTM_002 | 模型加载失败 | 模型文件损坏或不存在 |
| LSTM_003 | 预测失败 | 推理过程异常 |
| LSTM_004 | 数据格式错误 | 输入数据格式不正确 |

### 7.2 异常处理策略

| 异常类型 | 处理策略 |
|----------|----------|
| InsufficientDataError | 返回错误信息，要求至少60条 |
| ModelLoadError | 使用fallback模式 |
| PredictionError | 返回错误信息并记录日志 |

### 7.3 Fallback模式

当训练模型不存在时，使用随机初始化的模型，置信度降低到0.5。

```json
{
  "confidence": 0.5,
  "is_trained": false,
  "model_status": "fallback"
}
```

## 8. 监控与运维

### 8.1 监控指标

| 指标 | 告警阈值 | 说明 |
|------|----------|------|
| API响应时间 | > 5s | 单次预测耗时 |
| 模型推理时间 | > 2s | 模型推理耗时 |
| 错误率 | > 1% | 预测错误比例 |

### 8.2 日志设计

- 模型加载日志
- 预测耗时日志
- 错误堆栈日志

### 8.3 缓存策略

| 数据 | 缓存方式 | 过期时间 | 说明 |
|------|----------|----------|------|
| 模型对象 | 内存 | 长期 | 懒加载 |
| 预测结果 | Redis | 5分钟 | 避免重复预测 |

## 9. 任务清单

| 任务ID | 任务名称 | 优先级 | 依赖 | 预估工时 | 状态 |
|--------|----------|--------|------|----------|------|
| T-001 | LSTMService接口定义 | P0 | - | 1h | 待开始 |
| T-002 | LSTM模型构建 | P0 | T-001 | 3h | 待开始 |
| T-003 | 数据预处理模块 | P0 | - | 1h | 待开始 |
| T-004 | FastAPI路由定义 | P0 | T-001,T-002,T-003 | 1h | 待开始 |
| T-005 | Spring Boot Feign Client | P0 | T-004 | 2h | 待开始 |

### 9.1 任务详情

#### T-001: LSTMService接口定义

**文件路径**: `py-service/app/services/lstm.py`

**验收标准**:
- [ ] predict方法返回正确格式
- [ ] 支持批量预测
- [ ] 异常处理完善

#### T-002: LSTM模型构建

**文件路径**: `py-service/app/services/lstm.py`

**验收标准**:
- [ ] 模型结构正确
- [ ] 输入输出维度正确
- [ ] 模型可编译

#### T-005: Spring Boot Feign Client

**文件路径**: `stock-backend/.../client/LSTMClient.java`

**验收标准**:
- [ ] Feign Client正确
- [ ] DTO定义完整
- [ ] 超时配置合理

### 执行顺序

```
T-001 (接口) ──┐
T-002 (模型) ──┼──▶ T-004 (API) ──▶ T-005 (Client)
T-003 (预处理)─┘
```

## 10. 验收标准

### 10.1 功能验收

- [ ] 单股预测返回正确格式
- [ ] 批量预测支持50只股票
- [ ] 预测方向判断正确
- [ ] 置信度计算合理

### 10.2 性能验收

- [ ] 单股预测 < 1秒
- [ ] 50股批量预测 < 30秒
- [ ] 模型加载 < 10秒

### 10.3 可用性验收

- [ ] GPU不可用时自动降级CPU
- [ ] 异常情况返回错误信息
- [ ] 服务SLA > 99.5%

---

*文档版本: 1.0*
*最后更新: 2026-02-17*
*作者: mwangli*
