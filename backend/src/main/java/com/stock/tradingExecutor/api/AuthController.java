package com.stock.tradingExecutor.api;

import com.stock.tradingExecutor.domain.dto.CurrentUserDto;
import com.stock.tradingExecutor.domain.dto.CurrentUserResponseDto;
import com.stock.tradingExecutor.domain.dto.LoginRequestDto;
import com.stock.tradingExecutor.domain.dto.LoginResponseDto;
import com.stock.tradingExecutor.domain.dto.LogoutResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

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
    public ResponseEntity<CurrentUserResponseDto> currentUser() {
        CurrentUserDto user = CurrentUserDto.builder()
                .name("Admin")
                .avatar("https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png")
                .userid("00000001")
                .email("admin@stock.com")
                .signature("AI Trading System Admin")
                .title("Administrator")
                .group("Admin Group")
                .notifyCount(12)
                .unreadCount(11)
                .country("China")
                .access("admin")
                .build();

        CurrentUserResponseDto response = CurrentUserResponseDto.builder()
                .data(user)
                .success(true)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 登录接口
     */
    @PostMapping("/account")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto params) {
        String type = params.getType();
        String userName = params.getUserName();

        log.info("用户登录: {}, type={}", userName, type);

        LoginResponseDto response = LoginResponseDto.builder()
                .status("ok")
                .type(type)
                .currentAuthority("admin")
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * 退出登录
     */
    @PostMapping("/outLogin")
    public ResponseEntity<LogoutResponseDto> logout() {
        log.info("用户退出登录");
        LogoutResponseDto response = LogoutResponseDto.builder()
                .data(Collections.emptyMap())
                .success(true)
                .build();
        return ResponseEntity.ok(response);
    }

}
