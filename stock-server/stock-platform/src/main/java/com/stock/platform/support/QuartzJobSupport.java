// AI_GENERATE_START-------
package com.stock.platform.support;

import com.stock.platform.enums.JobTriggerSource;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

/**
 * 固定 Quartz Job 的上下文读取工具。
 * 不负责任务分派，只读取当前 Job 的触发来源和运行标识。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public final class QuartzJobSupport {

    private QuartzJobSupport() {
    }

    /**
     * 读取 Quartz 触发来源。
     *
     * @param context Quartz 执行上下文
     * @return 触发来源
     */
    public static JobTriggerSource triggerSource(JobExecutionContext context) {
        String value = data(context).getString("triggerSource");
        return value == null ? JobTriggerSource.SCHEDULED : JobTriggerSource.valueOf(value);
    }

    /**
     * 读取运行标识。
     *
     * @param context Quartz 执行上下文
     * @return 运行标识，可为空
     */
    public static String runId(JobExecutionContext context) {
        return data(context).getString("runId");
    }

    /**
     * 读取触发人。
     *
     * @param context Quartz 执行上下文
     * @return 触发人
     */
    public static String triggeredBy(JobExecutionContext context) {
        return data(context).getString("triggeredBy");
    }

    private static JobDataMap data(JobExecutionContext context) {
        return context.getMergedJobDataMap();
    }
}
// AI_GENERATE_END-------
