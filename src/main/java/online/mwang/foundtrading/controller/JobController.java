package online.mwang.foundtrading.controller;

import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.job.JobManager;
import org.quartz.CronExpression;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 14:37
 * @description: TestController
 */
@Slf4j
@RestController
@RequestMapping("/job")
public class JobController {

    @Autowired
    private JobManager jobManager;


    //  获取定时器信息
    @GetMapping()
    public String getJob(String name, String group) {
        String info = null;
        try {
            info = jobManager.getJobInfo(name, group);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        return info;
    }

    @PostMapping()
    public boolean modifyJob(String name, String group, String time) {
        boolean flag = true;
        if (!CronExpression.isValidExpression(time)) {
            throw new RuntimeException("非法的cron表达式");
        }
        try {
            flag = jobManager.modifyJob(name, group, time);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
        return flag;
    }

    // @Description: 启动所有定时器
    @PostMapping("/start")
    public void startQuartzJob() {
        try {
            jobManager.startJob();
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    // 暂停指定 定时器
    @PostMapping(value = "/pause")
    public void pauseQuartzJob(String name, String group) {
        try {
            jobManager.pauseJob(name, group);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    // 删除指定定时器
    @PostMapping(value = "/delete")
    public void deleteJob(String name, String group) {
        try {
            jobManager.deleteJob(name, group);
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }
}
