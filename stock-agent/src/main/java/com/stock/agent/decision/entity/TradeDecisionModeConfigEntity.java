// AI_GENERATE_START----
package com.stock.agent.decision.entity;

import org.springframework.data.annotation.Version;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 交易决策参与模式的单例治理配置。
 * 该配置只能由受控管理接口修改，普通聊天不能改变生产参与范围。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Accessors(chain = true)
@Document(collection = "trade_decision_mode_config")
public class TradeDecisionModeConfigEntity {

    /** 单例配置主键，固定为 1。 */
    @Id
    private Long configId;

    /** 配置的决策参与模式。 */
    private String decisionMode;

    /** Agent 判断不可用时的显式回退策略。 */
    private String fallbackPolicy;

    /** 允许参与的策略版本；为空表示不限制。 */
    private String strategyVersionScope;

    /** 允许参与的候选批次；为空表示不限制。 */
    private String candidateBatchScope;

    /** 按候选批次稳定散列计算的灰度比例，范围 0 至 100。 */
    private Integer rolloutPercentage;

    /** 是否允许 GATED 模式影响自动买入候选。 */
    private boolean productionImpactEnabled;

    /** 最近修改人。 */
    private String updatedBy;

    /** 最近修改时间。 */
    private LocalDateTime updatedAt;

    /** 乐观锁版本，用于防止并发覆盖治理配置。 */
    @Version
    private Long configVersion;
}
// AI_GENERATE_END----
