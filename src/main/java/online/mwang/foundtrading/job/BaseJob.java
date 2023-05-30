package online.mwang.foundtrading.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 09:16
 * @description: CommomJob
 */
@Slf4j
public abstract class BaseJob implements Job {

    /**
     * 任务执行方法
     */
    abstract void run();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        final long start = System.currentTimeMillis();
        run();
        final long end = System.currentTimeMillis();
        log.info("任务执行耗时{}秒。", (end - start) / 1000);
    }
}
