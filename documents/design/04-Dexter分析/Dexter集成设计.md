# Dexter分析模块设计

## 1. 文档信息

| 属性 | 内容 |
|------|------|
| 模块ID | MOD-004 |
| 模块名称 | Dexter分析 |
| 版本 | 1.0 |
| 状态 | 已完成 |
| 创建日期 | 2026-02-17 |
| 最后更新 | 2026-02-17 |
| 作者 | mwangli |

## 2. 概述

### 2.1 模块定位

Dexter分析模块是股票交易系统的基本面分析组件，负责通过调用Dexter AI API获取股票的财务分析、技术分析和投资建议，为综合选股模块提供基本面因子。

### 2.2 功能摘要

- 获取股票实时报价
- 获取股票基本面分析（估值、盈利、成长等）
- 获取技术分析数据（趋势、均线、RSI等）
- 获取交易建议（买入/卖出/持有）

### 2.3 上下游依赖

| 上游模块 | 接口 | 数据 |
|----------|------|------|
| 数据采集 | getStockInfo() | 股票基本信息 |

| 下游模块 | 接口 | 数据 |
|----------|------|------|
| 综合选股 | analyze() | 基本面得分和技术分析 |
| 决策引擎 | suggestion() | 交易建议 |

## 3. 功能需求

### 3.1 核心功能列表

| 功能ID | 功能名称 | 功能描述 | 优先级 |
|--------|----------|----------|--------|
| F-001 | 实时报价 | 获取股票实时行情数据 | P0 |
| F-002 | 基本面分析 | 获取股票财务分析数据 | P0 |
| F-003 | 技术分析 | 获取技术指标数据 | P0 |
| F-004 | 交易建议 | 获取AI交易建议 | P0 |
| F-005 | 批量分析 | 批量获取多只股票分析 | P1 |

### 3.2 功能详情

#### F-001: 实时报价

**功能描述**: 获取股票的实时报价数据。

**输入**: stockCode: 股票代码

**输出**:
```json
{
  "stock_code": "600519",
  "stock_name": "贵州茅台",
  "current_price": 1850.5,
  "price_change": 30.5,
  "price_change_percent": 1.68,
  "volume": 1234567,
  "amount": 2280000000,
  "timestamp": "2024-01-15T10:30:00"
}
```

#### F-002: 基本面分析

**功能描述**: 获取股票的财务分析数据，包括估值、盈利、成长等指标。

**输入**: stockCode: 股票代码

**输出**:
```json
{
  "stock_code": "600519",
  "fundamental": {
    "pe_ratio": 35.5,
    "pb_ratio": 12.3,
    "roe": 25.8,
    "revenue_growth": 15.2,
    "profit_growth": 18.5,
    "dividend_yield": 1.2,
    "debt_ratio": 0.25,
    "current_ratio": 2.5
  }
}
```

#### F-004: 交易建议

**功能描述**: 获取AI交易建议。

**输入**: stockCode: 股票代码

**输出**:
```json
{
  "stock_code": "600519",
  "suggestion": "buy",
  "confidence": 0.85,
  "reason": "基本面良好，技术面看涨",
  "target_price": 2000.0,
  "stop_loss": 1700.0
}
```

**业务规则**:
- suggestion: buy/sell/hold
- confidence: 0-1

## 4. 非功能需求

### 4.1 性能要求

| 指标 | 要求 | 说明 |
|------|------|------|
| 单次分析 | < 5s | 含网络请求 |
| 批量分析(10只) | < 30s | 串行请求 |
| API响应超时 | 30s | Dexter API |

### 4.2 可用性要求

- API调用失败自动重试3次
- 缓存失败数据，减少重复调用
- 服务SLA: 99%

### 4.3 成本控制

- 调用成本: 约¥0.01-0.05/次
- 建议自托管部署降低成本
- 缓存策略减少调用次数

## 5. 总体设计

### 5.1 技术架构

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| HTTP客户端 | httpx | 0.27.x |
| 缓存 | Redis | 7.x |
| 配置管理 | Pydantic Settings | - |
| Web框架 | FastAPI | 0.109.x |

### 5.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      FastAPI (py-service)                        │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    API Layer                              │   │
│  │   POST /api/dexter/analyze        (基本面分析)         │   │
│  │   POST /api/dexter/suggestion    (交易建议)            │   │
│  │   GET  /api/dexter/quote/{code} (实时报价)            │   │
│  │   POST /api/dexter/batch         (批量分析)            │   │
│  │   GET  /api/dexter/health        (健康检查)            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────▼──────────────────────────┐        │
│  │                  Service Layer                       │        │
│  │  ┌──────────────┐  ┌──────────────┐            │        │
│  │  │ DexterService │  │ CacheService │            │        │
│  │  │               │  │              │            │        │
│  │  │ - analyze()  │  │ - get/set   │            │        │
│  │  │ - suggestion │  │ - TTL管理    │            │        │
│  │  │ - quote()    │  │              │            │        │
│  │  └──────────────┘  └──────────────┘            │        │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│  ┌──────────────────────────▼──────────────────────────┐        │
│  │              External Integration                    │        │
│  │  ┌─────────────────────────────────────────────┐   │        │
│  │  │           Dexter Cloud API                   │   │        │
│  │  │  - Quote Data (实时行情)                   │   │        │
│  │  │  - Fundamental Analysis (基本面)           │   │        │
│  │  │  - Trading Suggestions (交易建议)          │   │        │
│  │  └─────────────────────────────────────────────┘   │        │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Backend                         │
│  ┌─────────────────┐  ┌─────────────────┐                      │
│  │ DexterClient   │  │ DexterAnalysisService                  │
│  │   (Feign)     │  │   (业务服务)     │                      │
│  └─────────────────┘  └─────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 部署架构

- 部署方式: Python微服务独立部署
- 端口: 8001
- 依赖: httpx, Redis
- 调用方式: HTTP REST API
- 成本: 自托管¥100-200/月，第三方¥500-2000/月

## 6. 详细设计

### 6.1 服务设计

#### 6.1.1 DexterService类

```python
class DexterService:
    """Dexter AI 客户端服务"""

    def __init__(self, api_key: str, base_url: str):
        self.api_key = api_key
        self.base_url = base_url
        self.timeout = 30
        self.max_retries = 3

    async def get_quote(self, stock_code: str) -> QuoteResult:
        """获取股票报价"""
        pass

    async def analyze_fundamental(self, stock_code: str) -> FundamentalResult:
        """基本面分析"""
        pass

    async def get_suggestion(self, stock_code: str) -> SuggestionResult:
        """获取交易建议"""
        pass

    async def batch_analyze(self, stock_codes: List[str]) -> List[FundamentalResult]:
        """批量基本面分析"""
        pass
```

#### 6.1.2 CacheService类

```python
class CacheService:
    """统一缓存服务"""

    def __init__(self):
        self.cache = {}  # 可替换为 Redis

    async def get(self, key: str) -> Optional[Any]:
        """获取缓存"""
        pass

    async def set(self, key: str, value: Any, ttl: int = 300):
        """设置缓存"""
        pass
```

### 6.2 接口设计

#### 6.2.1 接口列表

| 接口路径 | 方法 | 说明 |
|----------|------|------|
| /api/dexter/analyze | POST | 基本面分析 |
| /api/dexter/suggestion | POST | 交易建议 |
| /api/dexter/quote/{code} | GET | 实时报价 |
| /api/dexter/batch | POST | 批量分析 |
| /api/dexter/health | GET | 健康检查 |

#### 6.2.2 接口详情

**POST /api/dexter/analyze**

请求:
```json
{
  "stock_code": "600519",
  "include_quote": true,
  "analysis_type": ["financial", "technical", "news"]
}
```

响应:
```json
{
  "stock_code": "600519",
  "stock_name": "贵州茅台",
  "current_price": 1850.5,
  "price_change": 30.5,
  "price_change_percent": 1.68,
  "fundamental": {...},
  "technical": {...},
  "overall_score": 85,
  "suggestion": "buy",
  "analyzed_at": "2024-01-15T10:30:00"
}
```

### 6.3 调用流程

```
请求: POST /api/dexter/analyze
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  1. 参数校验                                                │
│  - stock_code: 6位数字                                       │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 缓存检查 (Redis)                                         │
│  - Key: dexter:analysis:{stock_code}                        │
│  - TTL: 1小时                                               │
│  - 命中 ──────────────────────────────────▶ 返回缓存        │
└─────────────────────────────────────────────────────────────┘
         │
         ▼ (未命中)
┌─────────────────────────────────────────────────────────────┐
│  3. 调用 Dexter API                                         │
│  - POST https://api.dexterai.com/v1/analyze                 │
│  - Headers: Authorization: Bearer {API_KEY}                │
│  - 重试: 3次，间隔2s                                        │
│  - 超时: 30s                                                │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 响应处理                                                │
│  - 解析 JSON                                                │
│  - 转换数据模型                                              │
│  - 计算综合评分                                              │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 缓存结果 & 返回                                          │
│  - 写入 Redis (TTL: 1小时)                                  │
│  - 返回响应                                                  │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 配置设计

```yaml
# Dexter分析服务配置
dexter:
  api:
    base-url: https://api.dexterai.com/v1
    api-key: ${DEXTER_API_KEY}
    timeout: 30
    max-retries: 3
    retry-delay: 2
  
  cache:
    enabled: true
    ttl: 3600  # 1小时
    redis:
      host: localhost
      port: 6379
      db: 0
```

## 7. 错误处理

### 7.1 错误码定义

| 错误码 | 说明 | HTTP状态 |
|--------|------|----------|
| 1001 | API Key无效 | 401 |
| 1002 | 股票代码不存在 | 404 |
| 1003 | Dexter服务不可用 | 503 |
| 1004 | 请求超时 | 504 |
| 1005 | 请求频率超限 | 429 |

### 7.2 错误响应格式

```json
{
  "error": {
    "code": 1003,
    "message": "Dexter 服务暂时不可用",
    "detail": "Connection refused after 3 retries"
  },
  "request_id": "req_abc123"
}
```

### 7.3 异常处理策略

| 异常类型 | 处理策略 |
|----------|----------|
| 401 InvalidKey | 返回错误，不重试 |
| 404 NotFound | 返回错误，不重试 |
| 503 Unavailable | 重试3次，失败后返回错误 |
| 429 RateLimit | 等待后重试 |
| Timeout | 重试3次，失败后返回错误 |

## 8. 监控与运维

### 8.1 监控指标

| 指标 | 告警阈值 | 说明 |
|------|----------|------|
| API响应时间 | > 10s | 含网络请求 |
| 错误率 | > 5% | API调用错误比例 |
| 缓存命中率 | < 60% | 缓存效率 |

### 8.2 日志设计

- API调用日志
- 错误堆栈日志
- 缓存命中/未命中日志

### 8.3 缓存策略

| 数据 | 缓存方式 | 过期时间 | 说明 |
|------|----------|----------|------|
| 基本面分析 | Redis | 1小时 | 减少API调用 |
| 实时报价 | Redis | 1分钟 | 价格实时性 |
| 交易建议 | Redis | 30分钟 | 建议稳定性 |

## 9. 任务清单

| 任务ID | 任务名称 | 优先级 | 依赖 | 预估工时 | 状态 |
|--------|----------|--------|------|----------|------|
| T-001 | DexterService客户端服务 | P0 | - | 1h | 待开始 |
| T-002 | 缓存服务 | P0 | - | 1h | 待开始 |
| T-003 | FastAPI路由定义 | P0 | T-001,T-002 | 1h | 待开始 |
| T-004 | Spring Boot Feign Client | P0 | T-003 | 1h | 待开始 |

### 9.1 任务详情

#### T-001: DexterService客户端服务

**文件路径**: `py-service/app/services/dexter.py`

**验收标准**:
- [ ] API调用正常
- [ ] 异常处理完善
- [ ] 重试机制正确

#### T-002: 缓存服务

**文件路径**: `py-service/app/services/cache.py`

**验收标准**:
- [ ] Redis连接正常
- [ ] TTL配置正确
- [ ] 缓存命中/未命中处理正确

#### T-004: Spring Boot Feign Client

**文件路径**: `stock-backend/.../client/DexterClient.java`

**验收标准**:
- [ ] Feign Client正确
- [ ] DTO定义完整

### 执行顺序

```
T-001 (Client) ──┐
T-002 (缓存) ────┼──▶ T-003 (API) ──▶ T-004 (Feign)
```

## 10. 验收标准

### 10.1 功能验收

- [ ] 实时报价获取成功
- [ ] 基本面分析数据完整
- [ ] 交易建议返回正确
- [ ] Spring Boot成功调用

### 10.2 性能验收

- [ ] 单次分析 < 5秒
- [ ] 批量分析(10只) < 30秒
- [ ] API超时30秒

### 10.3 可用性验收

- [ ] API失败自动重试
- [ ] 缓存正常工作
- [ ] 错误处理完善

---

*文档版本: 1.0*
*最后更新: 2026-02-17*
*作者: mwangli*
