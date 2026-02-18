package online.mwang.stockTrading.core.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.AdaptableJobFactory;
import org.springframework.stereotype.Component;

/**
 * Quartz Job 工厂
 */
@Component
public class CustomJobFactory extends AdaptableJobFactory {

    @Autowired
    private AutowireCapableBeanFactory capableBeanFactory;

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) {
        Class<?> clazz = bundle.getJobDetail().getJobClass();
        return capableBeanFactory.autowire(clazz, 4, true);
    }
}
