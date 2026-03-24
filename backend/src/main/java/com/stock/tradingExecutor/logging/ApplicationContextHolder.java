package com.stock.tradingExecutor.logging;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文持有器
 * 通过静态方式暴露 ApplicationContext，便于非 Spring 管理对象（如 Logback Appender）
 * 在运行时安全地获取到 Spring Bean。
 *
 * @author mwangli
 * @since 2026-03-11
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    /**
     * Spring 应用上下文单例引用
     */
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextHolder.context = applicationContext;
    }

    /**
     * 根据类型从 Spring 容器中获取 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   泛型参数
     * @return 指定类型的 Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("Spring ApplicationContext is not initialized yet.");
        }
        return context.getBean(clazz);
    }
}

