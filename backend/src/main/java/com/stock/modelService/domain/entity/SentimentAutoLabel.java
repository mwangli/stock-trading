package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 情感模型自动标注实体
 * 用于在 MongoDB 中管理情感分析数据的自动标注，
 * 支持基于交易反馈的半监督学习场景。
 *
 * <p>标注数据来源包括交易反馈（买入后 T+1 收益率为正/负），
 * 通过对比模型预测与实际收益，筛选高质量训练样本。</p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Data
@Document(collection = "sentiment_auto_labels")
public class SentimentAutoLabel {

    /**
     * MongoDB 文档 ID
     */
    @Id
    private String id;

    /**
     * 原文内容
     */
    private String text;

    /**
     * 标注标签：positive / neutral / negative
     */
    private String label;

    /**
     * 置信度 (0.0 ~ 1.0)
     */
    private Double confidence;

    /**
     * 来源：trading_feedback
     */
    private String source;

    /**
     * 关联股票代码
     */
    @Indexed(name = "idx_stock_code")
    private String stockCode;

    /**
     * T+1 收益率
     */
    private Double returnRate;

    /**
     * 模型预测标签
     */
    private String predictedLabel;

    /**
     * 状态：pending / validated / used / discarded
     */
    @Indexed(name = "idx_status")
    private String status;

    /**
     * 创建时间
     */
    @Indexed(name = "idx_created_at")
    private LocalDateTime createdAt;

    /**
     * 使用时间
     */
    private LocalDateTime usedAt;
}