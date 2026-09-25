// AI_GENERATE_START --
package com.stock.tradingExecutor.api;

import com.stock.tradingExecutor.domain.dto.AutoLoginResponseDto;
import com.stock.tradingExecutor.execution.AutoLoginService;
import com.stock.tradingExecutor.execution.BrowserSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 自动化登录控制器：一键登录、状态查询、浏览器关闭。
 * <p>支持无参数调用（使用 application.yml 中的默认账号密码），也支持手动传入账号密码。</p>
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@RestController
@RequestMapping("/api/auto-login")
@RequiredArgsConstructor
public class AutoLoginController {

    private final AutoLoginService autoLoginService;
    private final BrowserSessionManager browserSessionManager;

    /** 默认登录账号（资金账号），从配置文件读取 */
    @Value("${spring.auto-login.account:}")
    private String defaultAccount;

    /** 默认登录密码，从配置文件读取 */
    @Value("${spring.auto-login.password:}")
    private String defaultPassword;

    /**
     * 一键登录接口。
     * <p>username/password 为可选参数，不传则使用 application.yml 中配置的默认值。</p>
     *
     * @param username 账号（可选）
     * @param password 密码（可选）
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<AutoLoginResponseDto> login(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {
        // 1. 使用传入参数，若为空则使用默认配置
        String account = (username != null && !username.isBlank()) ? username : defaultAccount;
        String pwd = (password != null && !password.isBlank()) ? password : defaultPassword;

        if (account == null || account.isBlank() || pwd == null || pwd.isBlank()) {
            log.error("[AutoLoginController] 账号或密码为空，请传入参数或在配置文件中配置默认值");
            return ResponseEntity.badRequest().body(buildResponse(false, "账号或密码为空"));
        }

        log.info("[AutoLoginController] 接收登录请求: account={}", maskAccount(account));
        autoLoginService.printLoginStatus();

        boolean success = autoLoginService.login(account, pwd);

        autoLoginService.printLoginStatus();

        AutoLoginResponseDto response = buildResponse(success, success ? "登录成功" : "登录失败");

        if (success) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<AutoLoginResponseDto> getStatus() {
        boolean loggedIn = autoLoginService.isLoggedIn();
        AutoLoginResponseDto response = buildResponse(loggedIn, loggedIn ? "已登录" : "未登录");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/quit")
    public ResponseEntity<Void> quitBrowser() {
        try {
            browserSessionManager.quitBrowser();
            log.info("[AutoLoginController] 浏览器已关闭");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("[AutoLoginController] 关闭浏览器失败: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    // AI_GENERATED_START
    /**
     * 构建自动登录统一响应，补充浏览器诊断信息与人工介入路径。
     *
     * @param success 当前操作是否成功
     * @param message 当前结果提示
     * @return 自动登录响应 DTO
     */
    private AutoLoginResponseDto buildResponse(boolean success, String message) {
        return AutoLoginResponseDto.builder()
                .success(success)
                .message(message)
                // Token 仅保存在后端会话中，不返回给 PC 或小程序。
                .token(null)
                .stage(autoLoginService.getCurrentStage())
                .currentUrl(browserSessionManager.getCurrentUrl())
                .pageTitle(browserSessionManager.getPageTitle())
                .nextAction(autoLoginService.getNextAction())
                .smsCodeFile(browserSessionManager.getAutoLoginTmpDir().resolve("sms_code.txt").toString())
                .captchaCodeFile(browserSessionManager.getAutoLoginTmpDir().resolve("captcha_code.txt").toString())
                .noVncUrl("http://localhost:7900")
                .build();
    }

    private String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return "****";
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
    // AI_GENERATED_END
}
// AI_GENERATE_END --
