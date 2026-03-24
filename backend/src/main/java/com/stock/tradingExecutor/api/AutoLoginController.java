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
            return ResponseEntity.badRequest().body(AutoLoginResponseDto.builder()
                    .success(false).message("账号或密码为空").build());
        }

        log.info("[AutoLoginController] 接收登录请求: account={}", account);
        autoLoginService.printLoginStatus();

        boolean success = autoLoginService.login(account, pwd);

        autoLoginService.printLoginStatus();

        AutoLoginResponseDto response = AutoLoginResponseDto.builder()
                .success(success)
                .token(autoLoginService.getLoginToken())
                .message(success ? "登录成功" : "登录失败")
                .build();

        if (success) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<AutoLoginResponseDto> getStatus() {
        AutoLoginResponseDto response = AutoLoginResponseDto.builder()
                .success(autoLoginService.isLoggedIn())
                .token(autoLoginService.getLoginToken())
                .message(autoLoginService.isLoggedIn() ? "已登录" : "未登录")
                .build();
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
}
