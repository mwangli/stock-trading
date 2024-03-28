package online.mwang.stockTrading.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.AdaptableJobFactory;
import org.springframework.stereotype.Component;


@Component
public class CustomJobFactory extends AdaptableJobFactory{

    @Autowired
    private AutowireCapableBeanFactory capableBeanFactory;

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) {
        // 获取到需要注入的class
        Class<?> clazz = bundle.getJobDetail().getJobClass();
        // 进行注入
        return capableBeanFactory.autowire(clazz, 4, true);
    }
}