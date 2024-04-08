package online.mwang.stockTrading.web.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
import online.mwang.stockTrading.web.bean.po.QuartzJob;
import online.mwang.stockTrading.web.mapper.QuartzJobMapper;
import org.jetbrains.annotations.NotNull;
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

    @Override
    @SneakyThrows
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
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
                log.info("定时任务:{},加载完成", job.getName());
            } catch (Exception e) {
                log.info("定时任务{},加载异常:{}", job.getName(), e.getMessage());
            }
        }
    }
}
