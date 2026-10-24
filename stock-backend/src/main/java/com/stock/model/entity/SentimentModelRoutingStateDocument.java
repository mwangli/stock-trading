// AI_GENERATE_START ------
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * FinBERT 与 Mengzi 的人工激活、指标和自动回滚状态。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Document(collection = "sentiment_model_routing_states")
public class SentimentModelRoutingStateDocument {

    /** 固定路由状态主键。 */
    @Id
    private String id;

    /** 当前生产模型：FINBERT 或 MENGZI。 */
    private String activeModel;

    /** 上一生产模型，用于自动回滚。 */
    private String previousModel;

    /** 路由状态：SHADOW、ELIGIBLE、ACTIVE、ROLLBACK 或 BLOCKED。 */
    private String status;

    /** 已覆盖交易日数。 */
    private int shadowTradingDays;

    /** 已评估样本数。 */
    private long evaluatedSamples;

    /** 候选模型 Rank IC。 */
    private Double rankIc;

    /** FinBERT 基线 Rank IC。 */
    private Double baselineRankIc;

    /** 候选模型方向准确率。 */
    private Double directionAccuracy;

    /** FinBERT 基线方向准确率。 */
    private Double baselineDirectionAccuracy;

    /** 候选模型扣成本 Top 10% 收益。 */
    private Double topDecileNetReturn;

    /** FinBERT 基线扣成本 Top 10% 收益。 */
    private Double baselineTopDecileNetReturn;

    /** 候选模型输出标准差。 */
    private Double outputStd;

    /** 推理失败率。 */
    private Double failureRate;

    /** 候选模型非弃权覆盖率，范围 0 到 1。 */
    private Double coverageRate;

    /** FinBERT 基线非弃权覆盖率，范围 0 到 1。 */
    private Double baselineCoverageRate;

    /** 路由决策原因。 */
    private String decisionReason;

    /** 激活时间。 */
    private LocalDateTime activatedAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END ------
