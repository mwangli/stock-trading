# Dexter 集成设计

## 1. 模块概述

Dexter 分析模块负责提供股票基本面分析，通过调用 Dexter AI API 获取股票的财务分析、技术分析和投资建议。

## 2. 技术架构

### 2.1 技术栈

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| HTTP客户端 | httpx | 异步HTTP请求 |
| 缓存 | Redis | 分析结果缓存 |
| 配置文件 | Pydantic Settings | 环境变量管理 |

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      FastAPI (py-service)                        │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    API Layer                             │  │
│  │   POST /api/dexter/analyze          (基本面分析)         │  │
│  │   POST /api/dexter/suggestion        (交易建议)          │  │
│  │   GET  /api/dexter/quote/{code}     (实时报价)           │  │
│  │   POST /api/dexter/batch             (批量分析)           │  │
│  │   GET  /api/dexter/health            (健康检查)           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│  ┌──────────────────────────▼──────────────────────────┐     │
│  │                  Service Layer                        │     │
│  │                                                         │     │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────┐  │     │
│  │  │DexterService │  │ CacheService │  │  Metrics   │  │     │
│  │  │              │  │              │  │  Service   │  │     │
│  │  │ - analyze()  │  │ - get/set    │  │  监控指标  │  │     │
│  │  │ - suggestion │  │ - TTL管理    │  │            │  │     │
│  │  │ - quote()    │  │              │  │            │  │     │
│  │  └──────────────┘  └──────────────┘  └────────────┘  │     │
│  └──────────────────────────────────────────────────────────┘     │
│                              │                                  │
│  ┌──────────────────────────▼──────────────────────────┐     │
│  │                  External Integration                 │     │
│  │                                                         │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │              Dexter Cloud API                    │  │     │
│  │  │                                                 │  │     │
│  │  │  - Quote Data (实时行情)                        │  │     │
│  │  │  - Fundamental Analysis (基本面)                │  │     │
│  │  │  - Trading Suggestions (交易建议)               │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  └──────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
```

## 3. 服务设计

### 3.1 DexterService 类

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

### 3.2 CacheService 类

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

    async def get_fundamental(self, stock_code: str) -> Optional[FundamentalResult]:
        """获取缓存的基本面分析"""
        pass

    async def cache_fundamental(self, stock_code: str, result: FundamentalResult):
        """缓存基本面分析结果 (TTL: 1小时)"""
        pass
```

## 4. API 设计

### 4.1 基本面分析

**请求**:
```python
class DexterAnalyzeRequest(BaseModel):
    stock_code: str = Field(..., pattern=r"^\d{6}$")
    include_quote: bool = True
    analysis_type: List[str] = ["financial", "technical", "news"]
```

**响应**:
```python
class DexterAnalyzeResponse(BaseModel):
    stock_code: str
    stock_name: str
    current_price: float
    price_change: float
    price_change_percent: float
    fundamental: FundamentalAnalysis
    technical: TechnicalAnalysis
    sentiment: SentimentSummary
    overall_score: int          # 0-100
    suggestion: str             # buy/sell/hold
    analyzed_at: datetime
```

### 4.2 基本面数据字段

| 分类 | 字段 | 说明 |
|------|------|------|
| 估值 | pe_ratio | 市盈率 |
| 估值 | pb_ratio | 市净率 |
| 盈利 | roe | 净资产收益率 |
| 成长 | revenue_growth | 营收增长率 |
| 成长 | profit_growth | 净利润增长率 |
| 分红 | dividend_yield | 股息率 |
| 偿债 | debt_ratio | 资产负债率 |
| 偿债 | current_ratio | 流动比率 |

### 4.3 技术分析字段

| 字段 | 说明 |
|------|------|
| trend | 趋势 (up/down/sideways) |
| support_level | 支撑位 |
| resistance_level | 阻力位 |
| ma5 | 5日均线 |
| ma20 | 20日均线 |
| rsi | RSI指标 |

## 5. 调用流程

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

## 6. 错误处理

### 6.1 错误码定义

| 错误码 | 说明 | HTTP状态 |
|--------|------|----------|
| 1001 | API Key 无效 | 401 |
| 1002 | 股票代码不存在 | 404 |
| 1003 | Dexter 服务不可用 | 503 |
| 1004 | 请求超时 | 504 |
| 1005 | 请求频率超限 | 429 |

### 6.2 错误响应格式

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

## 7. 资源需求

| 资源 | 需求 |
|------|------|
| 内存 | ~128MB |
| 磁盘 | 无 |
| API调用 | 按需付费 |

## 8. 与后端集成

### 8.1 Feign Client

```java
@FeignClient(name = "dexter-service", url = "${ai.service.dexter.url}")
public interface DexterClient {
    @PostMapping("/api/dexter/analyze")
    DexterResponse analyze(@Body DexterRequest request);

    @PostMapping("/api/dexter/suggestion")
    SuggestionResponse getSuggestion(@Body SuggestionRequest request);

    @GetMapping("/api/dexter/quote/{stockCode}")
    QuoteResponse getQuote(@PathVariable("stockCode") String stockCode);
}
```

### 8.2 Service 封装

```java
@Service
public class DexterAnalysisService {
    private final DexterClient dexterClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public DexterResponse getAnalysis(String stockCode) {
        // 1. 尝试从缓存获取
        // 2. 缓存未命中，调用 Dexter API
        // 3. 缓存结果并返回
    }
}
```

---

*文档版本: 1.0*
*最后更新: 2026-02-17*
