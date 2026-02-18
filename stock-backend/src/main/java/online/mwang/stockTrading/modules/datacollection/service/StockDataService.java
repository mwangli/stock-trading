package online.mwang.stockTrading.modules.datacollection.service;

import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票数据采集服务接口
 */
public interface StockDataService {

    /**
     * 获取A股全市场股票列表
     * @return 可交易股票列表
     */
    List<StockInfo> fetchStockList();

    /**
     * 获取个股历史K线数据
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 历史价格列表
     */
    List<StockPrices> fetchHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate);

    /**
     * 获取个股近N天历史数据
     * @param stockCode 股票代码
     * @param days 天数
     * @return 历史价格列表
     */
    List<StockPrices> fetchHistoricalData(String stockCode, int days);

    /**
     * 获取实时行情数据
     * @param stockCode 股票代码
     * @return 当前价格
     */
    Double fetchRealTimePrice(String stockCode);

    /**
     * 获取财务报表数据
     * @param stockCode 股票代码
     * @return 财务数据JSON
     */
    String fetchFinancialReport(String stockCode);

    /**
     * 同步所有股票数据到本地数据库
     */
    void syncAllStocks();

    /**
     * 同步指定股票的历史数据
     * @param stockCode 股票代码
     * @param days 同步天数
     */
    void syncStockHistory(String stockCode, int days);
}
