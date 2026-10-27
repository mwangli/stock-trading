// AI_GENERATE_START--------
package com.stock.platform.support;

import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

/**
 * 支持协作式线程中断的固定 Quartz Job 基类。
 * Quartz 发出停止请求时中断当前执行线程，具体业务代码需要正确响应线程中断。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public abstract class InterruptibleQuartzJob implements InterruptableJob {

    private volatile Thread executionThread;

    /**
     * 记录当前执行线程并调用固定任务实现。
     *
     * @param context Quartz 执行上下文
     * @throws JobExecutionException Quartz 执行异常
     */
    @Override
    public final void execute(JobExecutionContext context) throws JobExecutionException {
        executionThread = Thread.currentThread();
        try {
            executeInterruptibly(context);
        } finally {
            executionThread = null;
        }
    }

    /**
     * 执行具体固定任务。
     *
     * @param context Quartz 执行上下文
     */
    protected abstract void executeInterruptibly(JobExecutionContext context);

    /**
     * 请求中断当前执行线程。
     *
     * @throws UnableToInterruptJobException 当前任务无法中断时抛出
     */
    @Override
    public void interrupt() throws UnableToInterruptJobException {
        Thread thread = executionThread;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
// AI_GENERATE_END--------
