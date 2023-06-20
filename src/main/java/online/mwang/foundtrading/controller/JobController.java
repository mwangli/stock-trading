package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.bean.query.QuartzJobQuery;
import online.mwang.foundtrading.job.DailyJob;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
import online.mwang.foundtrading.utils.RequestUtils;
import org.quartz.*;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 14:37
 * @description: TestController
 */
@Slf4j
@RestController
@RequestMapping("/job")
@RequiredArgsConstructor
public class JobController {
    private final static String TEMP_GROUP_NAME = "TEMP";
    private final Scheduler scheduler;
    private final QuartzJobMapper jobMapper;
    private final RequestUtils requestUtils;
    private final DailyJob dailyJob;

    @SneakyThrows
    @GetMapping()
    public Response<List<QuartzJob>> listJob(QuartzJobQuery query) {
        LambdaQueryWrapper<QuartzJob> queryWrapper = new QueryWrapper<QuartzJob>().lambda()
                .like(ObjectUtils.isNotNull(query.getName()), QuartzJob::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getClassName()), QuartzJob::getClassName, query.getClassName())
                .like(ObjectUtils.isNotNull(query.getCron()), QuartzJob::getCron, query.getCron())
                .eq(ObjectUtils.isNotNull(query.getStatus()), QuartzJob::getStatus, query.getStatus())
                .orderBy(true, true, QuartzJob.getOrder(query.getSortKey()));
        Page<QuartzJob> jobPage = jobMapper.selectPage(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(jobPage.getRecords(), jobPage.getTotal());
    }

    @SneakyThrows
    @PostMapping()
    public Response<Integer> createJob(@RequestBody QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            throw new RuntimeException("非法的cron表达式");
        }
        JobDetail jobDetail = JobBuilder.newJob((Class<Job>) Class.forName(job.getClassName())).withIdentity(job.getName()).build();
        CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).withSchedule(CronScheduleBuilder.cronSchedule(job.getCron())).build();
        scheduler.scheduleJob(jobDetail, cronTrigger);
        job.setStatus("1");
        job.setCreateTime(new Date());
        job.setUpdateTime(new Date());
        return Response.success(jobMapper.insert(job));
    }

    @SneakyThrows
    @PostMapping("run")
    public Response<?> runNow(@RequestBody QuartzJob job) {
        Trigger trigger = TriggerBuilder.newTrigger().startNow().build();
        JobDetail jobDetail = JobBuilder.newJob((Class<Job>) Class.forName(job.getClassName())).withIdentity(job.getName(), TEMP_GROUP_NAME).build();
        scheduler.scheduleJob(jobDetail, trigger);
        return Response.success();
    }

    @SneakyThrows
    @PutMapping()
    public Response<Integer> modifyJob(@RequestBody QuartzJob job) {
        // 取消交易等待
        String waiting = job.getWaiting();
        if (StringUtils.isNotBlank(waiting)) {
            dailyJob.setWaiting("waiting".equals(waiting));
        }
        // 是否打开接口日志
        String logSwitch = job.getLogSwitch();
        if (StringUtils.isNotBlank(logSwitch)) {
            requestUtils.setLogs("open".equals(logSwitch));
        }
        if (!CronExpression.isValidExpression(job.getCron())) {
            throw new RuntimeException("非法的cron表达式");
        }
        final QuartzJob quartzJob = jobMapper.selectById(job.getId());
        if (!quartzJob.getCron().equals(job.getCron())) {
            CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).withSchedule(CronScheduleBuilder.cronSchedule(job.getCron())).build();
            scheduler.rescheduleJob(TriggerKey.triggerKey(job.getName()), cronTrigger);
            job.setStatus("1");
        }
        job.setUpdateTime(new Date());
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @DeleteMapping()
    public Response<Integer> deleteJob(@RequestBody QuartzJob job) {
        if (job.getId() <= 13) return Response.success();
        scheduler.deleteJob(JobKey.jobKey(job.getName()));
        job.setDeleted("0");
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @PostMapping("/pause")
    public Response<Integer> pauseJob(@RequestBody QuartzJob job) {
        scheduler.pauseJob(JobKey.jobKey(job.getName()));
        job.setStatus("0");
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @PostMapping("/interrupt")
    public Response<Boolean> interruptJob(@RequestBody QuartzJob job) {
        final JobKey jobKey = JobKey.jobKey(job.getName());
        final JobKey tempKey = JobKey.jobKey(job.getName(), TEMP_GROUP_NAME);
        return Response.success(scheduler.interrupt(jobKey) | scheduler.interrupt(tempKey));
    }

    @SneakyThrows
    @PostMapping("/resume")
    public Response<Integer> resumeJob(@RequestBody QuartzJob job) {
        scheduler.resumeJob(JobKey.jobKey(job.getName()));
        job.setStatus("1");
        return Response.success(jobMapper.updateById(job));
    }
}
