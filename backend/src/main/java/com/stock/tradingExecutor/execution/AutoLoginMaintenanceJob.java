// AI_GENERATE_START --
package com.stock.tradingExecutor.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${spring.auto-login.account:}")
    private String username;

    @Value("${spring.auto-login.password:}")
    private String password;

    /**
     * 检查并维护登录状态。
     * 调用周期由统一的 JobSchedulerService 管理。
     */
    public void maintainLoginStatus() {
        try {
            log.info("[AutoLoginMaintenance] 开始检查登录状态");

            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                log.warn("[AutoLoginMaintenance] 未配置登录凭据，跳过自动登录维护");
                return;
            }
            
            if (!autoLoginService.isLoggedIn()) {
                log.warn("[AutoLoginMaintenance] 检测到未登录状态，开始自动登录");
                
                // 如果浏览器未启动，先启动
                if (!browserSessionManager.isRunning()) {
                    browserSessionManager.startBrowser();
                }
                
                boolean success = autoLoginService.login(username, password);
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
// AI_GENERATE_END --
