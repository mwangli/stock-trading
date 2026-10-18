// AI_GENERATE_START --
package com.stock.tradingExecutor.job;

/**
 * 任务触发来源。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public enum JobTriggerSource {

    /** Quartz 按 Cron 计划触发。 */
    SCHEDULED,

    /** 运维接口手动触发。 */
    MANUAL,

    /** 应用启动恢复检查触发。 */
    RECOVERY
}
// AI_GENERATE_END --
