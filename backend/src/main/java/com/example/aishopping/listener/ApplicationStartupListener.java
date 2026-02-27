package com.example.aishopping.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 项目启动监听器
 * 监听应用启动完成事件，输出启动成功日志
 */
@Component
@Slf4j
public class ApplicationStartupListener implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 只在根 ApplicationContext 初始化完成后执行一次
        if (event.getApplicationContext().getParent() == null) {
            log.info("========================================");
            log.info("项目启动成功！");
            log.info("服务地址: http://localhost:8080");
            log.info("健康检查: http://localhost:8080/api/health");
            log.info("========================================");
        }
    }
}
