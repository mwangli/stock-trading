// AI_GENERATE_START -
package com.stock.tradingExecutor.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.AdaptableJobFactory;

/**
 * Quartz Job 实例注入配置。
 * Quartz 直接创建固定 Job 类，再由 Spring 注入其业务依赖。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Configuration
public class QuartzConfig {

    /**
     * 为 Quartz 固定 Job 注入 Spring Bean 依赖。
     *
     * @param applicationContext Spring 应用上下文
     * @return Quartz 工厂定制器
     */
    @Bean
    public SchedulerFactoryBeanCustomizer quartzJobFactoryCustomizer(ApplicationContext applicationContext) {
        return factory -> factory.setJobFactory(new AdaptableJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
// AI_GENERATE_END -
