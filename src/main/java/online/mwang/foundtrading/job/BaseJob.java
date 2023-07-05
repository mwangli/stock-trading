package online.mwang.foundtrading.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String runningId;

    /**
     * 任务执行方法
     */
    abstract void run(String runningId);

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        final long start = System.currentTimeMillis();
        final String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        setRunningStatus(jobName, "1");
        this.runningId = UUID.randomUUID().toString();
        setRunningId(runningId);
        run(runningId);
        deleteRunningId(runningId);
        setRunningStatus(jobName, "0");
        final long end = System.currentTimeMillis();
        log.info("任务执行耗时{}秒。", (end - start) / 1000);
    }

    @Override
    public void interrupt() {
        log.info("正在终止任务...");
        deleteRunningId(runningId);
    }

    private void setRunningStatus(String jobName, String running) {
        final LambdaQueryWrapper<QuartzJob> queryWrapper = new LambdaQueryWrapper<QuartzJob>().eq(QuartzJob::getName, jobName);
        final QuartzJob quartzJob = jobMapper.selectOne(queryWrapper);
        quartzJob.setRunning(running);
        jobMapper.updateById(quartzJob);
    }

    private void setRunningId(String runningId) {
        stringRedisTemplate.opsForValue().set(runningId, "", 2, TimeUnit.HOURS);
    }

    private void deleteRunningId(String runningId) {
        stringRedisTemplate.opsForValue().getAndDelete(runningId);
    }
}
