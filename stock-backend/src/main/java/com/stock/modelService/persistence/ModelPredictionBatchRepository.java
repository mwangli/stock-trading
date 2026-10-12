// AI_GENERATE_START --
package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.ModelPredictionBatchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 模型预测批次仓库。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface ModelPredictionBatchRepository extends MongoRepository<ModelPredictionBatchDocument, String> {

    /**
     * 查询尚未记录交易日历来源的历史批次。
     *
     * @return 待回填日历来源的批次
     */
    List<ModelPredictionBatchDocument> findByCalendarFallbackIsNull();

    /**
     * 判断是否存在未结束预测批次。
     *
     * @param statuses 未结束状态集合
     * @return 是否存在未结束批次
     */
    boolean existsByStatusIn(Collection<String> statuses);

    /**
     * 查询未结束预测批次。
     *
     * @param statuses 未结束状态集合
     * @return 未结束批次列表
     */
    List<ModelPredictionBatchDocument> findByStatusIn(Collection<String> statuses);

    /**
     * 分页查询全部预测批次。
     *
     * @param pageable 分页条件
     * @return 预测批次分页结果
     */
    Page<ModelPredictionBatchDocument> findAll(Pageable pageable);

    /**
     * 查询最新预测批次。
     *
     * @return 最新批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findTopByOrderByCreatedAtDesc();

    /**
     * 查询同一模型和基准交易日最近批次，用于幂等控制。
     *
     * @param modelId 模型文档 ID
     * @param baseTradeDate 基准交易日
     * @return 最近批次，不存在时返回 null
     */
    ModelPredictionBatchDocument findFirstByModelIdAndBaseTradeDateOrderByCreatedAtDesc(
            String modelId, LocalDate baseTradeDate);
}
// AI_GENERATE_END --
