// AI_GENERATE_START--
package com.stock.base.job;

/**
 * 任务执行状态只读查询端口。
 * 上层能力通过该端口读取脱敏任务摘要，不依赖具体调度模块实现。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public interface JobExecutionQueryPort {

    /**
     * 查询指定任务最近一次执行摘要。
     *
     * @param jobName 任务名称
     * @return 最近执行摘要，不存在时返回 {@code null}
     */
    JobExecutionSnapshot findLatest(String jobName);
}
// AI_GENERATE_END--
