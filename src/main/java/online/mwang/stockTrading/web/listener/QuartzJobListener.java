package online.mwang.stockTrading.web.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.QuartzJob;
import online.mwang.stockTrading.web.mapper.ModelInfoMapper;
import online.mwang.stockTrading.web.mapper.QuartzJobMapper;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
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
    private final ModelInfoMapper modelInfoMapper;
    private final Scheduler scheduler;

    @Value("${profile:dev}")
    private String profile;

    @Override
    @SneakyThrows
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        // 清除模型训练状态
        modelInfoMapper.resetStatus();
        jobMapper.resetRunningStatus();
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
                if ("dev".equalsIgnoreCase(profile) && job.getName().contains("模型训练")) {
                    JobKey jobKey = JobKey.jobKey(job.getName());
                    scheduler.triggerJob(jobKey);
                    log.info("自动触发:{}", job.getName());
                }
            } catch (Exception e) {
                log.info("定时任务{},加载异常:{}", job.getName(), e.getMessage());
            }
        }
    }
}
