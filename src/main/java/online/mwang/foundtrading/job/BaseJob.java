package online.mwang.foundtrading.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.BusinessException;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
import online.mwang.foundtrading.utils.SleepUtils;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/30 09:16
 * @description: CommomJob
 */
@Slf4j
@Component
public abstract class BaseJob implements InterruptableJob {

    @Resource
    private QuartzJobMapper jobMapper;

    /**
     * 任务执行方法
     */
    abstract void run();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        final String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        setRunningStatus(jobName, "1");
        final long start = System.currentTimeMillis();
        try {
            run();
        } catch (BusinessException e) {
            log.info("任务终止成功!");
        }
        final long end = System.currentTimeMillis();
        setRunningStatus(jobName, "0");
        log.info("任务执行耗时{}秒。", (end - start) / 1000);
    }

    @Override
    public void interrupt() {
        log.info("正在终止任务...");
        SleepUtils.interrupted = true;
    }

    private void setRunningStatus(String jobName, String running) {
        final LambdaQueryWrapper<QuartzJob> queryWrapper = new LambdaQueryWrapper<QuartzJob>().eq(QuartzJob::getName, jobName);
        final QuartzJob quartzJob = jobMapper.selectOne(queryWrapper);
        quartzJob.setRunning(running);
        jobMapper.updateById(quartzJob);
    }
}
