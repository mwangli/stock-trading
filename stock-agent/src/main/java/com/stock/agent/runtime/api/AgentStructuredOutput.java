// AI_GENERATE_START-----
package com.stock.agent.runtime.api;

import java.util.List;

/**
 * Agent 结构化结果的最小公共契约。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentStructuredOutput {

    /**
     * 获取结构化摘要。
     *
     * @return 结果摘要
     */
    String getSummary();

    /**
     * 获取业务状态。
     *
     * @return 业务状态
     */
    String getStatus();

    /**
     * 获取证据引用。
     *
     * @return 证据编号列表
     */
    List<String> getEvidenceRefs();

    /**
     * 获取不确定性说明。
     *
     * @return 不确定性说明
     */
    String getUncertainty();
}
// AI_GENERATE_END----
