// AI_GENERATE_START -
package com.stock.strategyAgent.application;

import lombok.Getter;

/**
 * 任务运行记录的脱敏摘要。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Getter
public class JobExecutionSummary {

    /** 任务运行状态。 */
    private final String status;

    /** 脱敏后的错误摘要。 */
    private final String errorMessage;

    /**
     * 创建任务运行摘要。
     *
     * @param status 任务运行状态
     * @param errorMessage 脱敏后的错误摘要
     */
    public JobExecutionSummary(String status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }
}
// AI_GENERATE_END -
