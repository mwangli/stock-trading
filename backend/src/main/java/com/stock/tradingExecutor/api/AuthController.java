package com.stock.tradingExecutor.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器 (Mock)
 * 对应前端 /api/login/* 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/login")
public class AuthController {

    /**
     * 获取当前用户信息
     */
    @GetMapping("/currentUser")
    public ResponseEntity<Map<String, Object>> currentUser() {
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Admin");
        user.put("avatar", "https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
        user.put("userid", "00000001");
        user.put("email", "admin@stock.com");
        user.put("signature", "AI Trading System Admin");
        user.put("title", "Administrator");
        user.put("group", "Admin Group");
        user.put("notifyCount", 12);
        user.put("unreadCount", 11);
        user.put("country", "China");
        user.put("access", "admin");

        Map<String, Object> response = new HashMap<>();
        response.put("data", user);
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    /**
     * 登录接口
     */
    @PostMapping("/account")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String type = params.get("type");
        String userName = params.get("userName");

        log.info("用户登录: {}, type={}", userName, type);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("type", type);
        response.put("currentAuthority", "admin");
        return ResponseEntity.ok(response);
    }

    /**
     * 退出登录
     */
    @PostMapping("/outLogin")
    public ResponseEntity<Map<String, Object>> logout() {
        log.info("用户退出登录");
        Map<String, Object> response = new HashMap<>();
        response.put("data", new HashMap<>());
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取验证码 (Mock)
     */
    @GetMapping("/captcha")
    public ResponseEntity<String> captcha() {
        return ResponseEntity.ok("mock-captcha");
    }
    /**
     * 获取通知 (Mock)
     */
    @GetMapping("/../notices")
    public ResponseEntity<Map<String, Object>> getNotices() {
        Map<String, Object> response = new HashMap<>();
        response.put("data", new java.util.ArrayList<>());
        response.put("total", 0);
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}
