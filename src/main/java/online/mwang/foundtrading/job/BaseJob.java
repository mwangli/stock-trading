package online.mwang.foundtrading.job;

import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.utils.SleepUtils;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 09:16
 * @description: CommomJob
 */
@Slf4j
@Component
public abstract class BaseJob implements InterruptableJob {

    /**
     * 任务执行方法
     */
    abstract void run();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        final long start = System.currentTimeMillis();
        try {
            run();
        } catch (RuntimeException e) {
            log.info("任务终止成功!");
        }
        final long end = System.currentTimeMillis();
        log.info("任务执行耗时{}秒。", (end - start) / 1000);
    }

    @Override
    public void interrupt() {
        log.info("收到任务终止信号!");
        SleepUtils.interrupted = true;
    }
}
