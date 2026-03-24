package com.stock.tradingExecutor.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动登录状态维护任务
 * 定期检查登录状态，如未登录则自动重新登录
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoLoginMaintenanceJob {

    private final AutoLoginService autoLoginService;
    private final BrowserSessionManager browserSessionManager;
    
    // 从配置文件读取账号密码
    private static final String USERNAME = "13278828091";
    private static final String PASSWORD = "132553";

    /**
     * 每30分钟检查一次登录状态
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void maintainLoginStatus() {
        try {
            log.info("[AutoLoginMaintenance] 开始检查登录状态");
            
            if (!autoLoginService.isLoggedIn()) {
                log.warn("[AutoLoginMaintenance] 检测到未登录状态，开始自动登录");
                
                // 如果浏览器未启动，先启动
                if (!browserSessionManager.isRunning()) {
                    browserSessionManager.startBrowser();
                }
                
                boolean success = autoLoginService.login(USERNAME, PASSWORD);
                if (success) {
                    log.info("[AutoLoginMaintenance] 自动登录成功");
                } else {
                    log.error("[AutoLoginMaintenance] 自动登录失败");
                }
            } else {
                log.info("[AutoLoginMaintenance] 登录状态正常");
            }
            
        } catch (Exception e) {
            log.error("[AutoLoginMaintenance] 状态维护异常: {}", e.getMessage(), e);
        }
    }
}