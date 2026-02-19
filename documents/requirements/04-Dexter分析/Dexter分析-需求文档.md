aaa`# Dexter分析模块 - 需求规格说明书

## 文档信息

| 属性 | 值 |
|------|-----|
| 文档编号 | SRS-MOD-004 |
| 版本 | 1.0 |
| 创建日期 | 2026-02-17 |
| 最后更新 | 2026-02-17 |
| 模块ID | MOD-004 |

---

## 1. 引言

### 1.1 目的

本文档定义Dexter分析模块的详细需求，对接 Dexter AI API 获取股票基本面分析，为选股系统提供基本面因子。

### 1.2 范围

Dexter分析模块负责调用外部 Dexter AI 服务获取股票财务分析和交易建议，为综合选股提供基本面因子。

### 1.3 术语定义

| 术语 | 定义 |
|------|------|
| Dexter | 开源 AI 金融研究代理 (GitHub: virattt/dexter) |
| 基本面分析 | 分析公司财务状况、估值等指标 |
| PE/PB | 市盈率/市净率 |

### 1.4 Dexter 简介

**Dexter** 是一个开源的自主金融研究代理（GitHub: virattt/dexter，15,000+ Stars），具有以下特点：

- **自主研究能力**：能够分解复杂金融问题为步骤化研究计划
- **实时市场数据**：支持获取实时股票价格、财务报表等数据
- **多代理架构**：包含 Planner、Executor、Verifier、Answer 四个子代理
- **开源免费**：MIT 许可证，可自托管部署

---

## 2. 业务背景

### 2.1 业务场景

基本面分析是股票投资的重要参考因素。Dexter AI 提供专业的基本面分析和交易建议，通过集成其 API，可以为选股系统提供基本面因子。

### 2.2 部署方式

Dexter 可以通过以下两种方式部署：

| 部署方式 | 说明 | 成本 |
|----------|------|------|
| **自托管部署** | 在自有服务器/云上运行 Docker 容器 | 服务器成本（CPU/GPU） |
| **云端 API** | 使用第三方托管的 Dexter API 服务 | 按调用次数付费 |

**推荐方案**：自托管部署在 py-service 中，原因：
1. **成本可控**：一次性服务器投入，无按量费用
2. **数据隐私**：金融数据不出本地
3. **定制灵活**：可修改提示词和参数

### 2.3 模块依赖关系

```
MOD-001 数据采集
        ↓
MOD-004 Dexter分析
        ↓
MOD-005 综合选股
```

### 2.4 模块信息

| 属性 | 值 |
|------|-----|
| 模块ID | MOD-004 |
| 优先级 | P0 |
| 依赖模块 | MOD-001 数据采集 |
| 前置条件 | Dexter 服务已部署 |
| 后置条件 | 分析结果可供选股模块使用 |

---

## 3. 功能需求

### 3.1 股票基本面分析 (FR-001)

| 属性 | 描述 |
|------|------|
| 需求ID | FR-001 |
| 需求名称 | 股票基本面分析 |
| 优先级 | P0 |

**功能描述**：调用 Dexter API 获取股票基本面分析数据。

**输入**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stockCode | String | 是 | 股票代码 |

**输出**：

| 字段 | 类型 | 说明 |
|------|------|------|
| stockCode | String | 股票代码 |
| financialScore | Integer | 财务评分 (0-100) |
| growthScore | Integer | 成长评分 (0-100) |
| riskLevel | String | 风险等级 (high/medium/low) |
| suggestion | String | 交易建议 (buy/hold/sell) |
| targetPrice | Float | 目标价格 |

**业务规则**：
1. API 调用超时 30 秒
2. 失败重试 3 次
3. API 不可用时返回缓存数据

---

### 3.2 实时报价获取 (FR-002)

| 属性 | 描述 |
|------|------|
| 需求ID | FR-002 |
| 需求名称 | 实时报价获取 |
| 优先级 | P0 |

**功能描述**：通过 Dexter 获取股票实时报价。

**输出**：

| 字段 | 类型 | 说明 |
|------|------|------|
| stockCode | String | 股票代码 |
| currentPrice | Float | 当前价格 |
| change | Float | 涨跌额 |
| changePercent | Float | 涨跌幅 |

---

### 3.3 分析结果缓存 (FR-003)

| 属性 | 描述 |
|------|------|
| 需求ID | FR-003 |
| 需求名称 | 分析结果缓存 |
| 优先级 | P1 |

**功能描述**：缓存分析结果，减少 API 调用。

**缓存策略**：

| 数据类型 | TTL | Key格式 |
|----------|-----|---------|
| 基本面分析 | 1小时 | dexter:fundamental:{code} |
| 实时报价 | 1分钟 | dexter:quote:{code} |

---

## 4. 非功能需求

### 4.1 性能需求

| 指标 | 要求 | 说明 |
|------|------|------|
| API响应时间 | < 5s | Dexter API 调用 |
| 缓存命中响应 | < 100ms | Redis 缓存 |

### 4.2 可靠性需求

| 指标 | 要求 |
|------|------|
| API 可用性 | 95% |
| 缓存可用性 | 99.9% |

---

## 5. 接口定义

### 5.1 AI 服务接口 (Python)

```
POST /api/dexter/analyze       # 基本面分析
POST /api/dexter/suggestion    # 交易建议
GET  /api/dexter/quote/{code}  # 实时报价
```

### 5.2 后端调用接口 (Java)

```java
public interface DexterService {
    /** 获取基本面分析 */
    DexterResult analyze(String stockCode);
    
    /** 获取交易建议 */
    Suggestion getSuggestion(String stockCode);
    
    /** 获取缓存结果 */
    DexterResult getCached(String stockCode);
}
```

---

## 6. 外部依赖

### 6.1 Dexter 服务部署

Dexter 是开源项目，需要自行部署。主要有以下方式：

#### 方式一：Docker 本地部署（推荐）

```bash
# 克隆项目
git clone https://github.com/virattt/dexter.git
cd dexter

# 配置环境变量
cp .env.example .env
# 编辑 .env 设置 API Key 等参数

# 启动服务
docker-compose up -d

# 服务地址：http://localhost:8000
```

#### 方式二：云端 API 服务

如需使用第三方托管的 Dexter API，需要：
1. 注册第三方服务账号
2. 获取 API Key
3. 配置调用地址

### 6.2 API 调用方式

#### REST API 调用示例

```python
import httpx

# 基本面分析
response = httpx.post(
    "http://localhost:8000/api/dexter/analyze",
    json={"stock_code": "600519"},
    headers={"Authorization": "Bearer YOUR_API_KEY"},
    timeout=30.0
)
result = response.json()
```

#### 认证方式

| 方式 | 说明 |
|------|------|
| API Key | 通过 Header 传递：`Authorization: Bearer {API_KEY}` |
| 环境变量 | 设置 `DEXTER_API_KEY` |

### 6.3 成本估算

| 部署方式 | 成本项 | 月费用（估算） |
|----------|--------|----------------|
| **自托管（推荐）** | 云服务器 (2C4G) | ¥100-200/月 |
| | GPU (可选，用于加速) | ¥300-500/月 |
| | API Key (OpenAI/Anthropic) | 按调用量 |
| **第三方 API** | 按调用次数 | ¥500-2000/月 |

**成本优化策略**：
1. 使用缓存减少 API 调用（缓存 1 小时）
2. 批量分析减少请求次数
3. 使用 Claude Haiku 等低成本模型

### 6.4 依赖项

| 依赖项 | 提供方 | 用途 |
|--------|--------|------|
| Dexter 服务 | 自托管/第三方 | 基本面分析 API |
| OpenAI API | OpenAI | LLM 推理（自托管部署时需要） |
| Redis | 基础设施 | 结果缓存 |

---

## 7. 验收标准

### 7.1 功能验收

| 验收项 | 验收标准 | 验收方法 |
|--------|----------|----------|
| API调用 | Dexter API 可正常调用 | 集成测试 |
| 缓存 | 缓存命中返回正确数据 | 单元测试 |
| 错误处理 | API 不可用时返回缓存 | 集成测试 |

---

## 8. 相关文档

- [Dexter集成设计](../../design/04-Dexter分析/Dexter集成设计.md)
- [任务清单](../../design/04-Dexter分析/Dexter集成设计.md#9-任务清单)
