package online.mwang.stockTrading.web.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.QuartzJob;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.ModelInfoMapper;
import online.mwang.stockTrading.web.mapper.QuartzJobMapper;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.jetbrains.annotations.NotNull;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzJobListener implements ApplicationListener<ApplicationReadyEvent> {

    private final QuartzJobMapper jobMapper;
    private final ModelInfoMapper modelInfoMapper;
    private final ModelInfoService modelInfoService;
    private final StockInfoService stockInfoService;
    private final Scheduler scheduler;

    @Value("${profile}")
    private String profile;

    @Override
    @SneakyThrows
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        // 清除模型训练状态
        modelInfoMapper.resetStatus();
        jobMapper.resetRunningStatus();
        List<StockInfo> stockInfos = stockInfoService.list();
        List<ModelInfo> modelInfos = modelInfoService.list();
        Set<String> stockCodes = stockInfos.stream().map(StockInfo::getCode).collect(Collectors.toSet());
        Set<String> modelCodes = modelInfos.stream().map(ModelInfo::getCode).collect(Collectors.toSet());
        stockCodes.removeAll(modelCodes);
        log.info("获取到{}条待训练股票:", stockCodes.size());
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
                if ("prod".equalsIgnoreCase(profile) && stockCodes.size() > 0 && job.getName().contains("模型训练")) {
                    JobKey jobKey = JobKey.jobKey(job.getName());
                    scheduler.triggerJob(jobKey);
                    log.info("生产环境自动触发:{}", job.getName());
                }
                // 自动启用模型训练任务
            } catch (Exception e) {
                log.info("定时任务{},加载异常:{}", job.getName(), e.getMessage());
            }
        }
    }
}
