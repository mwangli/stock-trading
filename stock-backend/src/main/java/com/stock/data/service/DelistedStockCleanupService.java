// AI_GENERATE_START-------
package com.stock.data.service;

import com.stock.backend.config.CacheNames;
import com.stock.data.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 退市股票关联市场数据清理服务。
 * 只清理市场采集和模型派生数据，保留交易账本、持仓、成交和历史回测审计数据。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelistedStockCleanupService {

    private static final List<String> STOCK_CODE_COLLECTIONS = List.of(
            "stock_prices",
            "stock_news",
            "stock_price_5m",
            "stock_adjustment_factors",
            "model_stock_daily_bars",
            "stock_daily_tradability_facts",
            "model_predictions",
            "agent_rag_chunks"
    );

    private final StockInfoRepository stockInfoRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * 清理单只退市股票在 MongoDB 中的主数据、市场数据和派生数据。
     *
     * @param stockCode 股票代码
     * @return 各 MongoDB 集合删除数量之和
     */
    @CacheEvict(cacheNames = CacheNames.STOCK_INFO, key = "#stockCode")
    public long cleanup(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return 0;
        }
        String code = stockCode.trim();
        long deletedMongo = 0;
        for (String collection : STOCK_CODE_COLLECTIONS) {
            String field = collection.equals("stock_prices") ? "code"
                    : collection.equals("agent_rag_chunks") ? "stockCodes" : "stockCode";
            deletedMongo += mongoTemplate.remove(
                    Query.query(Criteria.where(field).is(code)), collection).getDeletedCount();
        }
        stockInfoRepository.deleteByCode(code);
        log.warn("退市股票关联数据已清理: stockCode={}, mongoDeleted={}", code, deletedMongo);
        return deletedMongo;
    }
}
// AI_GENERATE_END-------
