// AI_GENERATE_START----------
package com.stock.application.bootstrap;

import com.stock.StockBackendApplication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用就绪时在日志中打印启动耗时
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
public class StartupTimeLogger {

    @Order
    @EventListener(ApplicationReadyEvent.class)
    /** 在应用就绪后记录完整启动耗时。 */
    public void onApplicationReady() {
        long costMs = System.currentTimeMillis() - StockBackendApplication.getStartTimeMs();
        log.info("========== 项目启动完成，耗时: {} ms ({} 秒) ==========", costMs, String.format("%.2f", costMs / 1000.0));
    }
}
// AI_GENERATE_END----------
