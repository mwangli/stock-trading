package online.mwang.stockTrading.config;

import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.jobs.*;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 交易任务调度配置
 * 根据股票交易时间配置定时任务：
 * - 09:30-11:30: 上午交易时段，止损检查
 * - 13:00-15:00: 下午交易时段
 * - 14:50-14:55: 买入执行时间
 */
@Slf4j
@Configuration
public class TradingSchedulerConfig {

    /**
     * 上午交易时段Job - 止损检查
     * 每天 09:30 执行
     */
    @Bean
    public JobDetail morningTradingJobDetail() {
        return JobBuilder.newJob(MorningTradingJob.class)
                .withIdentity("morningTradingJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger morningTradingTrigger() {
        // 每天 09:30 执行
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule("0 30 09 * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(morningTradingJobDetail())
                .withIdentity("morningTradingTrigger", "trading")
                .withSchedule(schedule)
                .build();
    }

    /**
     * 下午交易时段Job - 止损检查
     * 每天 13:30 执行
     */
    @Bean
    public JobDetail afternoonTradingJobDetail() {
        return JobBuilder.newJob(AfternoonTradingJob.class)
                .withIdentity("afternoonTradingJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger afternoonTradingTrigger() {
        // 每天 13:30 执行
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule("0 30 13 * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(afternoonTradingJobDetail())
                .withIdentity("afternoonTradingTrigger", "trading")
                .withSchedule(schedule)
                .build();
    }

    /**
     * 买入执行Job
     * 每天 14:50 执行
     */
    @Bean
    public JobDetail buyExecutionJobDetail() {
        return JobBuilder.newJob(BuyExecutionJob.class)
                .withIdentity("buyExecutionJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger buyExecutionTrigger() {
        // 每天 14:50 执行
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule("0 50 14 * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(buyExecutionJobDetail())
                .withIdentity("buyExecutionTrigger", "trading")
                .withSchedule(schedule)
                .build();
    }

    /**
     * 收盘后任务 - 撤销无效订单
     * 每天 15:10 执行
     */
    @Bean
    public JobDetail endOfDayJobDetail() {
        return JobBuilder.newJob(EndOfDayJob.class)
                .withIdentity("endOfDayJob", "trading")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger endOfDayTrigger() {
        // 每天 15:10 执行
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule("0 10 15 * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(endOfDayJobDetail())
                .withIdentity("endOfDayTrigger", "trading")
                .withSchedule(schedule)
                .build();
    }
}
