package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelTrainingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 模型训练记录仓库
 * 负责对 MySQL 中的 {@link ModelTrainingRecord} 实体进行 CRUD 操作。
 *
 * <p>主要用于根据股票代码分页查询训练记录，以及按代码集合批量加载记录。</p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Repository
public interface ModelTrainingRecordRepository extends JpaRepository<ModelTrainingRecord, Long> {

    /**
     * 按股票代码模糊匹配或名称模糊匹配分页查询
     *
     * @param stockCode 股票代码关键字
     * @param stockName 股票名称关键字
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<ModelTrainingRecord> findByStockCodeContainingIgnoreCaseOrStockNameContainingIgnoreCase(
            String stockCode, String stockName, Pageable pageable);

    /**
     * 按股票代码集合批量查询记录
     *
     * @param stockCodes 股票代码集合
     * @return 记录列表
     */
    List<ModelTrainingRecord> findByStockCodeIn(Collection<String> stockCodes);
}

