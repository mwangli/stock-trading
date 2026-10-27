// AI_GENERATE_START---
package com.stock.strategy.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 自适应策略页面的模型版本比较项。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class AdaptiveStrategyVersionItem {

    /** 版本角色，取值为 ACTIVE、PREVIOUS 或 CANDIDATE。 */
    String role;

    /** 模型版本文档标识。 */
    String versionId;

    /** 模型业务版本。 */
    String modelVersion;

    /** 模型版本状态。 */
    String status;

    /** 模型架构类型。 */
    String architectureType;

    /** 特征契约版本。 */
    String featureVersion;

    /** Walk-Forward 平均 Rank IC，可为空。 */
    Double meanRankIc;

    /** Walk-Forward Rank ICIR，可为空。 */
    Double rankIcIr;

    /** Walk-Forward Top 10% 成本后收益，可为空。 */
    Double topDecileNetReturn;

    /** 是否检测到模型输出塌缩，可为空。 */
    Boolean outputCollapsed;

    /** 版本记录创建时间，不代表生产激活时间。 */
    LocalDateTime createdAt;
}
// AI_GENERATE_END---
