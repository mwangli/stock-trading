package com.stock.config;

import com.stock.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用就绪时在日志中打印启动耗时（从 main 到 ApplicationReadyEvent）
 */
@Slf4j
@Component
public class StartupTimeLogger {

    @Order
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        long costMs = System.currentTimeMillis() - Application.getStartTimeMs();
        log.info("========== 项目启动完成，耗时: {} ms ({} 秒) ==========", costMs, String.format("%.2f", costMs / 1000.0));
    }
}
