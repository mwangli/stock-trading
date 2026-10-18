// AI_GENERATE_START -
package com.stock.strategyAgent.rag;

import lombok.Builder;
import lombok.Value;

/**
 * Embedding Provider 脱敏运行状态。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Value
@Builder
public class EmbeddingRuntimeStatus {

    /** 是否启用 Embedding。 */
    boolean enabled;

    /** 当前 Embedding Provider。 */
    String provider;

    /** 配置是否完整。 */
    boolean configured;

    /** 当前 Bean 是否可用。 */
    boolean ready;

    /** 当前模型名称。 */
    String model;

    /** 当前模型版本。 */
    String modelVersion;

    /** 预期向量维度。 */
    int dimension;

    /** 当前状态。 */
    String status;

    /** 是否使用 hash 降级。 */
    boolean fallback;
}
// AI_GENERATE_END -
