// AI_GENERATE_START --
package com.stock.agent.runtime.api;

/**
 * 外部权威事实源状态查询端口。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public interface FactSourceStatusPort {

    /**
     * 按需探测事实源，不参与 Agent 启动门禁。
     *
     * @return 当前事实源状态
     */
    FactSourceStatus status();
}
// AI_GENERATE_END -
