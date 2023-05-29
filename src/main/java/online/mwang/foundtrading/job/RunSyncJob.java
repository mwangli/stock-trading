package online.mwang.foundtrading.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunSyncJob implements Job {

    private final DailyJob job;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        final long start = System.currentTimeMillis();
        job.runSyncJob();
        final long end = System.currentTimeMillis();
        log.info("任务执行耗时{}秒。", (end - start) / 1000);
    }
}
