// AI_GENERATE_START -
package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.StockPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 股票日线价格仓库。
 * 提供完整时间序列、日期区间和最近窗口查询，最近窗口查询用于限制模型训练与推理的内存占用。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Repository
public interface PriceRepository extends MongoRepository<StockPrice, String> {

    /**
     * 按日期升序查询股票完整日线。
     *
     * @param code 股票代码
     * @return 完整日线列表
     */
    List<StockPrice> findByCodeOrderByDateAsc(String code);

    /**
     * 按日期倒序分页查询股票最近日线。
     *
     * @param code 股票代码
     * @param pageable 分页参数，用于限制返回条数
     * @return 最近日线列表，日期倒序
     */
    List<StockPrice> findByCodeOrderByDateDesc(String code, Pageable pageable);

    /**
     * 查询股票最新一条日线。
     *
     * @param code 股票代码
     * @return 最新日线
     */
    Optional<StockPrice> findTopByCodeOrderByDateDesc(String code);

    /**
     * 查询指定日期区间日线并按日期升序返回。
     *
     * @param code 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期区间日线
     */
    List<StockPrice> findByCodeAndDateBetweenOrderByDateAsc(
            String code, LocalDate startDate, LocalDate endDate);

    /**
     * 查询股票指定交易日日线。
     *
     * @param code 股票代码
     * @param date 交易日
     * @return 指定交易日日线
     */
    Optional<StockPrice> findByCodeAndDate(String code, LocalDate date);

    /**
     * 删除股票全部日线。
     *
     * @param code 股票代码
     */
    void deleteByCode(String code);

    /**
     * 判断股票指定交易日日线是否存在。
     *
     * @param code 股票代码
     * @param date 交易日
     * @return 是否存在
     */
    boolean existsByCodeAndDate(String code, LocalDate date);

    /**
     * 判断股票是否已有日线。
     *
     * @param code 股票代码
     * @return 是否存在
     */
    boolean existsByCode(String code);
}
// AI_GENERATE_END -