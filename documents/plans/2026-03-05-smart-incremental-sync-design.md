# 智能增量同步 (Smart Incremental Sync) 设计文档

## 1. 背景与目标

当前 `StockDataService` 的同步机制采用"固定数量拉取 + 全量 Upsert"的方式，存在以下问题：
1.  **效率低**：每日仅需更新 1 条数据，却拉取并写入 500 条。
2.  **无法补缺**：如果服务中断 3 天，恢复后固定拉取可能无法精确覆盖缺口（虽然 500 条通常够用，但逻辑上不严谨）。
3.  **IO 浪费**：网络带宽和数据库 IO 存在大量无用功。

**目标**：实现基于"最新同步时间"的智能增量更新，支持断点续传。

## 2. 核心设计

### 2.1. 总体流程

1.  **获取最后更新时间**：对每一只股票，先查询数据库中已存在的**最新日期** (`lastSyncDate`)。
2.  **计算缺口**：
    - 如果数据库无记录 -> 视为新股，拉取默认区间（如近 3 年）。
    - 如果有记录 -> 计算 `startDate = lastSyncDate + 1 天`。
3.  **判断是否需要更新**：
    - 如果 `startDate > 今日` -> 数据已最新，跳过。
    - 否则 -> 进入拉取流程。
4.  **区间拉取**：调用证券接口，仅请求 `[startDate, 今日]` 的数据。
5.  **批量写入**：将获取到的增量数据 Upsert 入库。

### 2.2. 模块改造

#### A. 数据访问层 (`PriceRepository`)

需要新增查询方法，用于获取某只股票的最新一条价格记录。

```java
// PriceRepository.java
Optional<StockPrice> findTopByCodeOrderByDateDesc(String code);
```

#### B. 客户端层 (`SecuritiesClient`)

扩展接口，支持按日期范围拉取。

```java
// SecuritiesClient.java
// 新增重载方法
JSONArray getHistoryPrices(String code, LocalDate startDate, LocalDate endDate);
```

#### C. 业务逻辑层 (`StockDataService`)

重构 `syncAllStocksHistory` 方法，核心逻辑如下：

```java
public void syncStock(String code) {
    // 1. 查最后日期
    Optional<StockPrice> lastPrice = priceRepository.findTopByCodeOrderByDateDesc(code);
    
    LocalDate startDate;
    if (lastPrice.isEmpty()) {
        // 无历史数据，初始化拉取近 3 年
        startDate = LocalDate.now().minusYears(3);
    } else {
        // 有历史数据，从次日开始
        startDate = lastPrice.get().getDate().plusDays(1);
    }
    
    LocalDate today = LocalDate.now();
    
    // 2. 检查是否需要更新
    if (startDate.isAfter(today)) {
        return; // 已最新
    }
    
    // 3. 拉取区间数据
    List<StockPrice> newPrices = securitiesClient.getHistoryPrices(code, startDate, today);
    
    // 4. 入库
    if (!newPrices.isEmpty()) {
        saveStockPrices(newPrices);
    }
}
```

## 3. 异常处理与兜底

1.  **接口不支持精确区间**：
    - 如果底层 API 只能传 `count`，则计算 `days = (today - startDate)`，并加少量 buffer（如 +5 天）防止时区差异，拉取后在内存中二次过滤。
2.  **数据空洞**：
    - 如果某只股票停牌 1 个月，拉取回来的数据可能为空，系统应记录日志并正常结束，视为该区间无数据。
3.  **首次初始化**：
    - 系统初次运行时，所有股票都会触发 "无历史数据" 逻辑，自动执行全量初始化。

## 4. 性能预期

- **日常更新**：每只股票仅拉取 1 条数据，数据库写入量减少 99%。
- **断服恢复**：自动计算缺口（如 7 天），精准补齐，不重不漏。
