// AI_GENERATE_START--------
package com.stock.data.repository;

import com.stock.data.entity.StockInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 股票基本信息 MongoDB 仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface StockInfoRepository extends MongoRepository<StockInfo, String> {

    Optional<StockInfo> findByCode(String code);

    boolean existsByCode(String code);

    List<StockInfo> findByMarket(String market);

    List<StockInfo> findByNameContaining(String name);

    List<StockInfo> findByCodeIn(List<String> codes);

    void deleteByCode(String code);

    /**
     * 查询当前可交易股票代码。
     *
     * @return 已排除 ST 和退市标记的股票代码
     */
    default List<String> findAllCodes() {
        return findAll().stream()
                .filter(StockInfoRepository::isTradable)
                .map(StockInfo::getCode)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 统计当前可交易股票数量。
     *
     * @return 可交易股票数量
     */
    default long countTradable() {
        return findAll().stream().filter(StockInfoRepository::isTradable).count();
    }

    private static boolean isTradable(StockInfo stock) {
        return isActive(stock.getName())
                && isActive(stock.getSecurityCategory())
                && isActive(stock.getMarket());
    }

    private static boolean isActive(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return !normalized.contains("ST")
                && !value.contains("退")
                && !normalized.contains("DELIST")
                && !normalized.contains("TERMINATED");
    }
}
// AI_GENERATE_END--------
