package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.StockInfo;
import online.mwang.stockTrading.entities.StockPrices;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票数据服务接口
 * 【架构变更】数据采集已迁移到Python层
 *
 * 原功能：数据采集 + 数据查询
 * 新功能：仅数据查询（从MySQL/MongoDB/Redis读取）
 *
 * 数据采集流程变更：
 * 旧：Java -> HTTP调用Python API -> 返回数据 -> Java写入数据库
 * 新：Python服务 -> 直接采集 -> 直接写入MySQL/MongoDB/Redis
 *
 * 注意：以下方法已弃用，将逐步移除：
 * - fetchStockList() -> 使用 queryStockList() 从MySQL查询
 * - fetchHistoricalData() -> 使用 queryHistoricalData() 从MongoDB查询
 * - fetchRealTimePrice() -> 使用 queryRealTimePrice() 从Redis/MySQL查询
 * - syncAllStocks() -> Python层APScheduler定时执行
 * - syncStockHistory() -> Python层APScheduler定时执行
 */
public interface StockDataService {

    // ===============================
    // 查询接口（新 - 从数据库读取）
    // ===============================

    /**
     * 从MySQL查询股票列表
     * @return 可交易股票列表
     */
    List<StockInfo> queryStockList();

    /**
     * 从MongoDB查询历史K线数据
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 历史价格列表
     */
    List<StockPrices> queryHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate);

    /**
     * 从MongoDB查询历史K线数据
     * @param stockCode 股票代码
     * @param days 天数
     * @return 历史价格列表
     */
    List<StockPrices> queryHistoricalData(String stockCode, int days);

    /**
     * 从Redis/MySQL查询实时行情
     * @param stockCode 股票代码
     * @return 当前价格
     */
    Double queryRealTimePrice(String stockCode);

    /**
     * 触发Python服务执行数据同步（可选HTTP调用）
     * 用于手动触发同步任务
     */
    void triggerDataSync();

    // ===============================
    // 已弃用接口（原数据采集，现移至Python层）
    // ===============================

    /**
     * 【已弃用】使用 queryStockList()
     * 数据采集已迁移到Python层直接写入MySQL
     */
    @Deprecated(since = "2.0", forRemoval = true)
    List<StockInfo> fetchStockList();

    /**
     * 【已弃用】使用 queryHistoricalData()
     * 数据采集已迁移到Python层直接写入MongoDB
     */
    @Deprecated(since = "2.0", forRemoval = true)
    List<StockPrices> fetchHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate);

    /**
     * 【已弃用】使用 queryHistoricalData()
     * 数据采集已迁移到Python层直接写入MongoDB
     */
    @Deprecated(since = "2.0", forRemoval = true)
    List<StockPrices> fetchHistoricalData(String stockCode, int days);

    /**
     * 【已弃用】使用 queryRealTimePrice()
     * 实时行情采集已迁移到Python层更新Redis/MySQL
     */
    @Deprecated(since = "2.0", forRemoval = true)
    Double fetchRealTimePrice(String stockCode);

    /**
     * 【已弃用】功能未实现
     */
    @Deprecated(since = "2.0", forRemoval = true)
    String fetchFinancialReport(String stockCode);

    /**
     * 【已弃用】使用Python APScheduler定时任务
     * 数据同步已迁移到Python层自动执行
     */
    @Deprecated(since = "2.0", forRemoval = true)
    void syncAllStocks();

    /**
     * 【已弃用】使用Python APScheduler定时任务
     * 数据同步已迁移到Python层自动执行
     */
    @Deprecated(since = "2.0", forRemoval = true)
    void syncStockHistory(String stockCode, int days);
}
