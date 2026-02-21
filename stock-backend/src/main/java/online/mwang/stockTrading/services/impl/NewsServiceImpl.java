package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.entities.StockNews;
import online.mwang.stockTrading.services.NewsService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 财经新闻服务实现
 * 从MongoDB查询Python服务写入的新闻数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsServiceImpl implements NewsService {
    
    private final MongoTemplate mongoTemplate;
    
    private static final String NEWS_COLLECTION = "news";
    
    @Override
    public List<StockNews> getNewsByStockCode(String stockCode) {
        try {
            Query query = new Query(Criteria.where("stockCode").is(stockCode));
            query.limit(100); // 默认最多返回100条
            return mongoTemplate.find(query, StockNews.class, NEWS_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to get news for stock {}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }
    
    @Override
    public List<StockNews> getLatestNewsByStockCode(String stockCode, int limit) {
        try {
            Query query = new Query(Criteria.where("stockCode").is(stockCode));
            query.limit(limit);
            // 按发布时间倒序排列
            query.with(Sort.by(Sort.Direction.DESC, "publishTime"));
            return mongoTemplate.find(query, StockNews.class, NEWS_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to get latest news for stock {}: {}", stockCode, e.getMessage());
            return List.of();
        }
    }
    
    @Override
    public List<StockNews> getMarketNews(int limit) {
        try {
            Query query = new Query();
            query.limit(limit);
            // 按发布时间倒序排列
            query.with(Sort.by(Sort.Direction.DESC, "publishTime"));
            return mongoTemplate.find(query, StockNews.class, NEWS_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to get market news: {}", e.getMessage());
            return List.of();
        }
    }
    
    @Override
    public List<StockNews> getNewsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            Query query = new Query(
                Criteria.where("publishTime").gte(startTime).lte(endTime)
            );
            query.limit(500); // 最多返回500条
            return mongoTemplate.find(query, StockNews.class, NEWS_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to get news by time range: {}", e.getMessage());
            return List.of();
        }
    }
    
    @Override
    public long getTotalNewsCount() {
        try {
            return mongoTemplate.count(new Query(), StockNews.class, NEWS_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to get total news count: {}", e.getMessage());
            return 0;
        }
    }
    
    @Override
    public List<StockNews> searchNews(String keyword, int limit) {
        try {
            Query query = new Query(
                new Criteria().orOperator(
                    Criteria.where("title").regex(keyword, "i"),
                    Criteria.where("content").regex(keyword, "i")
                )
            );
            query.limit(limit);
            query.with(Sort.by(Sort.Direction.DESC, "publishTime"));
            return mongoTemplate.find(query, StockNews.class, NEWS_COLLECTION);
        } catch (Exception e) {
            log.error("Failed to search news with keyword {}: {}", keyword, e.getMessage());
            return List.of();
        }
    }
}
