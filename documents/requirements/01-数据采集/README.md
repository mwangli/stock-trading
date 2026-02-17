# 数据采集模块

## 模块职责

负责从外部数据源采集A股行情数据、财经新闻数据，为其他模块提供数据支撑。

## 需求列表

### 1. A股行情数据需求

| 需求ID | 需求描述 | 优先级 | 数据来源 |
|--------|----------|--------|----------|
| DR-001 | 获取A股全市场股票列表（2000+只） | P0 | AKTools |
| DR-002 | 获取个股历史K线数据（近3年） | P0 | AKTools |
| DR-003 | 获取实时行情数据（每分钟更新） | P0 | AKTools |
| DR-004 | 获取财务报表数据（年报/季报） | P0 | AKTools |

### 2. 财经新闻数据需求

| 需求ID | 需求描述 | 优先级 | 数据来源 |
|--------|----------|--------|----------|
| DR-005 | 爬取个股财经新闻 | P0 | 新浪财经/东方财富 |
| DR-006 | 新闻去重与分类存储 | P1 | 系统内部 |
| DR-007 | 重点关注股票新闻优先处理 | P1 | 策略优化 |

### 3. 定时任务需求

| 任务名称 | 执行时间 | 执行频率 | 说明 |
|----------|----------|----------|------|
| 数据同步 | 9:00-14:55 | 每分钟 | 实时行情同步 |
| 新闻采集 | 9:30-11:30 | 每30分钟 | 盘中新闻 |

### 4. 非功能需求

| 指标 | 要求 |
|------|------|
| 数据延迟 | < 1分钟 |
| 选股耗时 | < 30秒 |

## 外部依赖

- AKTools API
- 新浪财经
- 东方财富

## 输出接口

```java
public interface StockDataService {
    List<StockInfo> fetchStockList();
    List<DailyPrice> fetchHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate);
    List<RealTimePrice> fetchRealTimePrices(List<String> stockCodes);
    List<News> fetchStockNews(String stockCode);
}
```
