package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.QuartzJob;
import online.mwang.stockTrading.web.mapper.QuartzJobMapper;
import online.mwang.stockTrading.web.utils.DateUtils;
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

    abstract void run();

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        final String jobName = jobExecutionContext.getJobDetail().getKey().getName();
        log.info("{}, 任务执行开始====================================", jobName);
        final long start = System.currentTimeMillis();
        setRunningStatus(jobName, "1");
        try {
            run();
        } catch (Exception e) {
            log.error("任务执行出错！请查看下列异常信息栈：");
            e.printStackTrace();
        }
        setRunningStatus(jobName, "0");
        final long end = System.currentTimeMillis();
        log.info("{} ,任务执行结束====================================", jobName);
        log.info("{}, 任务执行耗时{}秒。", jobName, DateUtils.timeConvertor(end - start));
    }

    @Override
    public void interrupt() {
        log.info("正在尝试终止任务...");
    }

    private void setRunningStatus(String jobName, String running) {
        final LambdaQueryWrapper<QuartzJob> queryWrapper = new LambdaQueryWrapper<QuartzJob>()
                .eq(QuartzJob::getName, jobName);
        final QuartzJob quartzJob = jobMapper.selectOne(queryWrapper);
        quartzJob.setRunning(running);
        jobMapper.updateById(quartzJob);
    }
}
