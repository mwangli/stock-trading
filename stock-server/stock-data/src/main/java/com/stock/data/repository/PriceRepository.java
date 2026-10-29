// AI_GENERATE_START--------------
package com.stock.data.repository;

import com.stock.data.entity.StockPrice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 股票日线价格仓库。
 * 提供完整时间序列、日期区间和最近窗口查询，最近窗口查询用于限制模型训练与推理的内存占用。
 *
 * @author mwangli
 * @since 2026-10-29
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
     * 按历史截止日期倒序查询股票日线，用于复现历史预测时点的输入窗口。
     *
     * @param code 股票代码
     * @param endDate 行情截止日期，包含当天
     * @param pageable 分页参数，用于限制返回条数
     * @return 截止指定日期的日线列表，日期倒序
     */
    List<StockPrice> findByCodeAndDateLessThanEqualOrderByDateDesc(
            String code, LocalDate endDate, Pageable pageable);

    /**
     * 按来源版本查询指定股票的最近日线。
     *
     * @param code 股票代码
     * @param source 数据来源
     * @param sourceVersion 数据契约版本
     * @param pageable 查询窗口
     * @return 日期倒序的统一日线
     */
    List<StockPrice> findByCodeAndSourceAndSourceVersionOrderByDateDesc(
            String code, String source, String sourceVersion, Pageable pageable);

    /**
     * 按来源版本查询指定股票截止日期的最近日线。
     *
     * @param code 股票代码
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据契约版本
     * @param pageable 查询窗口
     * @return 日期倒序的统一日线
     */
    List<StockPrice> findByCodeAndDateLessThanEqualAndSourceAndSourceVersionOrderByDateDesc(
            String code, LocalDate endDate, String source, String sourceVersion, Pageable pageable);

    /**
     * 批量查询固定来源版本的股票日线。
     *
     * @param codes 股票代码集合
     * @param source 数据来源
     * @param sourceVersion 数据契约版本
     * @return 按股票代码升序、日期倒序排列的统一日线
     */
    List<StockPrice> findByCodeInAndSourceAndSourceVersionOrderByCodeAscDateDesc(
            Collection<String> codes, String source, String sourceVersion);

    /**
     * 批量查询固定来源版本截止日期的股票日线。
     *
     * @param codes 股票代码集合
     * @param endDate 截止日期，包含当天
     * @param source 数据来源
     * @param sourceVersion 数据契约版本
     * @return 按股票代码升序、日期倒序排列的统一日线
     */
    List<StockPrice> findByCodeInAndDateLessThanEqualAndSourceAndSourceVersionOrderByCodeAscDateDesc(
            Collection<String> codes, LocalDate endDate, String source, String sourceVersion);

    /**
     * 查询固定来源版本的最新日线。
     *
     * @param source 数据来源
     * @param sourceVersion 数据契约版本
     * @return 最新统一日线
     */
    Optional<StockPrice> findFirstBySourceAndSourceVersionOrderByDateDesc(
            String source, String sourceVersion);

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
     * 批量查询股票在指定交易日的日线，用于预测实际结果回填。
     *
     * @param codes 股票代码集合
     * @param date 交易日
     * @return 指定交易日的股票日线
     */
    List<StockPrice> findByCodeInAndDate(List<String> codes, LocalDate date);

    /**
     * 统计指定股票集合在目标交易日的行情数量，用于预测和评估入队前的覆盖率门禁。
     *
     * @param codes 股票代码集合
     * @param date 交易日
     * @return 匹配的行情数量
     */
    long countByCodeInAndDate(List<String> codes, LocalDate date);

    /**
     * 查询全市场最新一条日线，用于确定预测批次基准日期。
     *
     * @return 全市场最新日线
     */
    Optional<StockPrice> findTopByOrderByDateDesc();

    /**
     * 删除股票全部日线。
     *
     * @param code 股票代码
     */
    void deleteByCode(String code);

}
// AI_GENERATE_END--------------
