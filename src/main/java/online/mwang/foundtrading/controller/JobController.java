package online.mwang.foundtrading.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.BaseQuery;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.po.QuartzJob;
import online.mwang.foundtrading.mapper.QuartzJobMapper;
import org.quartz.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 14:37
 * @description: TestController
 */
@Slf4j
@RestController
@RequestMapping("job")
@RequiredArgsConstructor
public class JobController {

    private final Scheduler scheduler;
    private final QuartzJobMapper jobMapper;

    @SneakyThrows
    public static void main(String[] args) {
        String s = "online.mwang.foundtrading.job.RunTokenJob";
        Class<?> aClass = Class.forName(s);
    }

    @SneakyThrows
    @GetMapping()
    public Response<List<QuartzJob>> listJob(@RequestBody BaseQuery query) {
        Page<QuartzJob> jobPage = jobMapper.selectPage(Page.of(query.getCurrent(), query.getPageSize()), new QueryWrapper<>());
        return Response.success(jobPage.getRecords(), jobPage.getTotal());
    }

    @SneakyThrows
    @PostMapping()
    public Response<Integer> createJob(@RequestBody QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            throw new RuntimeException("非法的cron表达式");
        }
        startJob(job);
        return Response.success(jobMapper.insert(job));
    }

    @SneakyThrows
    public void startJob(QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            Class<Job> clazz = (Class<Job>) Class.forName(job.getClassName());
            JobDetail jobDetail = JobBuilder.newJob(clazz).withIdentity(job.getName()).build();
            CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).withSchedule(CronScheduleBuilder.cronSchedule(job.getCron())).build();
            scheduler.scheduleJob(jobDetail, cronTrigger);
        }
    }

    @SneakyThrows
    @PostMapping("run")
    public void runNow(@RequestBody QuartzJob job) {
        Class<Job> clazz = (Class<Job>) Class.forName(job.getClassName());
        JobDetail jobDetail = JobBuilder.newJob(clazz).withIdentity(job.getClassName()).build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).startNow().build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    @SneakyThrows
    @PutMapping()
    public Response<Integer> modifyJob(@RequestBody QuartzJob job) {
        if (!CronExpression.isValidExpression(job.getCron())) {
            throw new RuntimeException("非法的cron表达式");
        }
        CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity(job.getName()).withSchedule(CronScheduleBuilder.cronSchedule(job.getName())).build();
        scheduler.rescheduleJob(TriggerKey.triggerKey(job.getName()), cronTrigger);
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @DeleteMapping()
    public Response<Integer> deleteJob(@RequestBody QuartzJob job) {
        scheduler.deleteJob(JobKey.jobKey(job.getName()));
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @PostMapping(value = "pause")
    public Response<Integer> pauseJob(@RequestBody QuartzJob job) {
        scheduler.pauseJob(JobKey.jobKey(job.getName()));
        return Response.success(jobMapper.updateById(job));
    }

    @SneakyThrows
    @PostMapping(value = "resume")
    public Response<Integer> resumeJob(@RequestBody QuartzJob job) {
        scheduler.pauseJob(JobKey.jobKey(job.getName()));
        return Response.success(jobMapper.updateById(job));
    }
}
