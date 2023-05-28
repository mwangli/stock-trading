package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.bean.query.QuartzJobQuery;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
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


    private final static String ASCEND = "ascend";
    private final Scheduler scheduler;
    private final QuartzJobMapper jobMapper;

    @SneakyThrows
    @GetMapping()
    public Response<List<QuartzJob>> listJob(QuartzJobQuery query) {
        LambdaQueryWrapper<QuartzJob> queryWrapper = new QueryWrapper<QuartzJob>().lambda()
                .like(ObjectUtils.isNotNull(query.getName()), QuartzJob::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getClassName()), QuartzJob::getClassName, query.getClassName())
                .like(ObjectUtils.isNotNull(query.getCron()), QuartzJob::getCron, query.getCron())
                .eq(ObjectUtils.isNotNull(query.getStatus()), QuartzJob::getStatus, query.getStatus())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), QuartzJob.getOrder(query.getSortKey()));
        Page<QuartzJob> jobPage = jobMapper.selectPage(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
        return Response.success(jobPage.getRecords(), jobPage.getTotal());
    }

    @SneakyThrows
    @PostMapping()
    public Response<Integer> createJob(@RequestBody QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            throw new RuntimeException("非法的cron表达式");
        }
        startJob(job);
        job.setStatus("1");
        job.setCreateTime(new Date());
        job.setUpdateTime(new Date());
        return Response.success(jobMapper.insert(job));
    }

    @SneakyThrows
    public void startJob(QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            JobDetail jobDetail = JobBuilder.newJob((Class<Job>) Class.forName(job.getClassName())).withIdentity(job.getName()).build();
            CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).withSchedule(CronScheduleBuilder.cronSchedule(job.getCron())).build();
            scheduler.scheduleJob(jobDetail, cronTrigger);
            scheduler.start();
        }
    }

    @SneakyThrows
    @PostMapping("run")
    public Response<?> runNow(@RequestBody QuartzJob job) {
        Trigger trigger = TriggerBuilder.newTrigger().startNow().build();
        JobDetail jobDetail = JobBuilder.newJob((Class<Job>) Class.forName(job.getClassName())).withIdentity(job.getName()).build();
        scheduler.scheduleJob(jobDetail, trigger);
//        Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
//        SleepUtils.second(2);
//        Trigger.TriggerState triggerState2 = scheduler.getTriggerState(trigger.getKey());
//        Class<?> aClass = Class.forName(job.getClassName());

//        Method[] declaredMethods = Class.forName(job.getClassName()).getDeclaredMethods();
//        for (Method method : declaredMethods) {
//            if (method.getName().equals("execute")){
//                method.invoke()
//            }
//        }
        return Response.success();
    }

    @SneakyThrows
    @PutMapping()
    public Response<Integer> modifyJob(@RequestBody QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            throw new RuntimeException("非法的cron表达式");
        }
        CronTrigger cronTrigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(job.getCron())).build();
        scheduler.rescheduleJob(TriggerKey.triggerKey(job.getName()), cronTrigger);
        job.setUpdateTime(new Date());
        return Response.success(jobMapper.updateById(job));
    }


    @SneakyThrows
    @DeleteMapping()
    public Response<Integer> deleteJob(@RequestBody QuartzJob job) {
        scheduler.deleteJob(JobKey.jobKey(job.getName()));
        return Response.success(jobMapper.deleteById(job.getId()));
    }

    @SneakyThrows
    @PostMapping(value = "/pause")
    public Response<Integer> pauseJob(@RequestBody QuartzJob job) {
        scheduler.pauseJob(JobKey.jobKey(job.getName()));
        job.setStatus("0");
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @PostMapping(value = "/resume")
    public Response<Integer> resumeJob(@RequestBody QuartzJob job) {
        scheduler.pauseJob(JobKey.jobKey(job.getName()));
        job.setStatus("1");
        return Response.success(jobMapper.updateById(job));
    }
}