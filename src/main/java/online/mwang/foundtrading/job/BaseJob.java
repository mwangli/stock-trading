package online.mwang.foundtrading.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 09:16
 * @description: CommomJob
 */
@Slf4j
public abstract class BaseJob implements InterruptableJob {

    /**
     * 任务执行方法
     */
    abstract void run();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        final long start = System.currentTimeMillis();
        run();
        final long end = System.currentTimeMillis();
        log.info("任务执行耗时{}秒。", (end - start) / 1000);
    }

    @Override
    public void interrupt() {
        Thread.currentThread().interrupt();
//        interrupt = true;
        log.info("任务终止！");
    }
}
