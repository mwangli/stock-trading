// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

/**
 * 固定任务因业务条件不满足而主动跳过的异常。
 *
 * @author mwangli
 * @since 2026-10-13
 */
public class JobSkippedException extends RuntimeException {

    /**
     * 创建任务跳过异常。
     *
     * @param message 跳过原因
     */
    public JobSkippedException(String message) {
        super(message);
    }
}
// AI_GENERATE_END -
