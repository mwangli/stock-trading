package online.mwang.foundtrading.config;


import lombok.SneakyThrows;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class QuartzConfig {

    @Autowired
    private CustomJobFactory customJobFactory;

    @Bean
    @SneakyThrows
    public Scheduler scheduler() {
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();
        // 自定义 JobFactory 使得在 Quartz Job 中可以使用 @Autowired
        scheduler.setJobFactory(customJobFactory);
        scheduler.start();
        return scheduler;
    }
}