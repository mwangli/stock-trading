package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.StockNews;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 财经新闻服务接口
 * 
 * 功能：
 * - 从MongoDB查询财经新闻
 * - 获取指定股票的新闻
 * - 获取市场新闻
 * 
 * 注意：新闻采集由Python服务执行，此处仅提供查询接口
 */
public interface NewsService {
    
    /**
     * 获取指定股票的新闻
     * @param stockCode 股票代码
     * @return 新闻列表
     */
    List<StockNews> getNewsByStockCode(String stockCode);
    
    /**
     * 获取指定股票的最新N条新闻
     * @param stockCode 股票代码
     * @param limit 返回数量限制
     * @return 新闻列表
     */
    List<StockNews> getLatestNewsByStockCode(String stockCode, int limit);
    
    /**
     * 获取市场新闻
     * @param limit 返回数量限制
     * @return 新闻列表
     */
    List<StockNews> getMarketNews(int limit);
    
    /**
     * 获取指定时间范围内的新闻
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 新闻列表
     */
    List<StockNews> getNewsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 获取所有新闻数量
     * @return 新闻总数
     */
    long getTotalNewsCount();
    
    /**
     * 根据关键词搜索新闻
     * @param keyword 关键词
     * @param limit 返回数量限制
     * @return 新闻列表
     */
    List<StockNews> searchNews(String keyword, int limit);
}
