package online.mwang.stockTrading.repositories;

import online.mwang.stockTrading.entities.StockPrices;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 股票价格 MongoDB Repository
 */
@Repository
public interface StockPricesRepository extends MongoRepository<StockPrices, String> {

    /**
     * 根据股票代码查询价格列表
     * @param code 股票代码
     * @return 价格列表
     */
    List<StockPrices> findByCode(String code);

    /**
     * 根据股票代码和日期查询
     * @param code 股票代码
     * @param date 日期
     * @return 价格信息
     */
    Optional<StockPrices> findByCodeAndDate(String code, String date);

    /**
     * 根据股票代码和日期范围查询
     * @param code 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 价格列表
     */
    List<StockPrices> findByCodeAndDateBetween(String code, String startDate, String endDate);

    /**
     * 根据股票代码删除
     * @param code 股票代码
     */
    void deleteByCode(String code);

    /**
     * 检查是否存在
     * @param code 股票代码
     * @param date 日期
     * @return 是否存在
     */
    boolean existsByCodeAndDate(String code, String date);
}
