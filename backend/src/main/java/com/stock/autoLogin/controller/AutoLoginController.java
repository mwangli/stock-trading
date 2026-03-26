package com.stock.autoLogin.controller;

import com.stock.autoLogin.dto.LoginResult;
import com.stock.autoLogin.service.AutoLoginService;
import com.stock.autoLogin.service.BrowserSessionManager;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动登录控制器
 * 提供一键登录、登录状态查询、浏览器管理等接口
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/auto-login")
@RequiredArgsConstructor
public class AutoLoginController {

    private final AutoLoginService autoLoginService;
    private final BrowserSessionManager browserSessionManager;

    @Value("${spring.auto-login.account:}")
    private String defaultAccount;

    @Value("${spring.auto-login.password:}")
    private String defaultPassword;

    /**
     * 一键登录接口
     *
     * @param username 可选，不传则使用默认配置
     * @param password 可选，不传则使用默认配置
     * @return ResponseDTO<LoginResult>
     */
    @PostMapping("/login")
    public ResponseDTO<LoginResult> login(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password
    ) {
        log.info("收到登录请求：username={}, password={}",
                maskUsername(username), "***");

        String account = username != null && !username.isEmpty() ? username : defaultAccount;
        String pwd = password != null && !password.isEmpty() ? password : defaultPassword;

        LoginResult result = autoLoginService.executeLogin(account, pwd);

        if (result.isSuccess()) {
            return ResponseDTO.success(result, "登录成功");
        } else {
            return ResponseDTO.failure(result.getMessage());
        }
    }

    /**
     * 查询当前登录状态
     */
    @GetMapping("/status")
    public ResponseDTO<Map<String, Object>> loginStatus() {
        log.info("查询登录状态");
        Map<String, Object> status = new HashMap<>();
        status.put("isLoggedIn", autoLoginService.isLoggedIn());
        status.put("lastLoginTime", autoLoginService.getLastLoginTime());
        status.put("browserAlive", browserSessionManager.isBrowserAlive());
        return ResponseDTO.success(status);
    }

    /**
     * 关闭浏览器
     */
    @PostMapping("/quit")
    public ResponseDTO<Void> quitBrowser() {
        log.info("关闭浏览器");
        browserSessionManager.quitBrowser();
        return ResponseDTO.success(null, "浏览器已关闭");
    }

    /**
     * 强制退出登录
     */
    @PostMapping("/logout")
    public ResponseDTO<Void> logout() {
        log.info("强制退出登录");
        autoLoginService.forceLogout();
        return ResponseDTO.success(null, "已退出登录");
    }

    private String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "(默认)";
        }
        if (username.length() <= 3) {
            return username.substring(0, 1) + "***";
        }
        return username.substring(0, 3) + "***";
    }
}
