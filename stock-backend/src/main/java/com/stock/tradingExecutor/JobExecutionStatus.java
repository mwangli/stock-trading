// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

/**
 * 任务运行状态。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public enum JobExecutionStatus {

    /** 已排队，等待 Quartz 工作线程执行。 */
    QUEUED,

    /** 正在执行。 */
    RUNNING,

    /** 执行成功。 */
    SUCCESS,

    /** 执行失败。 */
    FAILED,

    /** 因业务条件不满足而跳过。 */
    SKIPPED,

    /** 因门禁不通过而拒绝。 */
    REJECTED,

    /** 被取消或应用停止中断。 */
    CANCELLED
}
// AI_GENERATE_END -
