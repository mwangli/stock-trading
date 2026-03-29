package com.stock.modelService.persistence;

import com.stock.modelService.domain.entity.SentimentAutoLabel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 情感自动标注仓库
 * 负责对 MongoDB 中的 {@link SentimentAutoLabel} 文档进行 CRUD 操作。
 *
 * <p>主要用于管理基于交易反馈的自动标注数据，支持按状态和置信度查询，
 * 以及批量获取待处理的标注样本。</p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Repository
public interface SentimentAutoLabelRepository extends MongoRepository<SentimentAutoLabel, String> {

    /**
     * 按状态查询标注
     *
     * @param status 状态：pending / validated / used / discarded
     * @return 标注列表
     */
    List<SentimentAutoLabel> findByStatus(String status);

    /**
     * 按状态查询且置信度不低于指定值
     *
     * @param status     状态
     * @param confidence 最低置信度
     * @return 标注列表
     */
    List<SentimentAutoLabel> findByStatusAndConfidenceGreaterThanEqual(String status, Double confidence);

    /**
     * 统计指定状态的标注数量
     *
     * @param status 状态
     * @return 数量
     */
    long countByStatus(String status);

    /**
     * 获取待处理的标注样本（按创建时间倒序取前100条）
     *
     * @param status 状态
     * @return 待处理标注列表
     */
    List<SentimentAutoLabel> findTop100ByStatusOrderByCreatedAtDesc(String status);
}