package online.mwang.foundtrading.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.job.AllJobs;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzJobListener implements ApplicationListener<ApplicationReadyEvent> {

    private final QuartzJobMapper jobMapper;
    private final Scheduler scheduler;
    private final AllJobs allJobs;

    @Override
    @SneakyThrows
    public void onApplicationEvent(ApplicationReadyEvent event) {
        final LambdaQueryWrapper<QuartzJob> queryWrapper = new LambdaQueryWrapper<QuartzJob>().eq(QuartzJob::getDeleted, "1");
        List<QuartzJob> jobs = jobMapper.selectList(queryWrapper);
        for (QuartzJob job : jobs) {
            try {
                JobDetail jobDetail = JobBuilder.newJob((Class<Job>) Class.forName(job.getClassName())).withIdentity(job.getName()).build();
                CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).withSchedule(CronScheduleBuilder.cronSchedule(job.getCron())).build();
                scheduler.scheduleJob(jobDetail, cronTrigger);
                if ("0".equals(job.getStatus())) {
                    scheduler.pauseJob(JobKey.jobKey(job.getName()));
                }
                // 交易时间段内，自动触发买卖任务
                if (allJobs.inTradingTimes() && (job.getClassName().endsWith("RunBuyJob") || job.getClassName().endsWith("RunSaleJob"))) {
                    Trigger trigger = TriggerBuilder.newTrigger().startNow().build();
                    scheduler.scheduleJob(jobDetail, trigger);
                    log.info("交易时间段内，自动触发买卖任务!");
                }
            } catch (Exception e) {
                log.info("定时任务加载异常:{}", job);
            }
        }
        log.info("Quartz定时任务加载完成。");
    }
}
