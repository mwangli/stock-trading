// AI_GENERATE_START-------
package com.stock.trading.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.trading.dto.AuthUserDto;
import com.stock.trading.dto.LoginRequestDto;
import com.stock.trading.entity.UserEntity;
import com.stock.trading.service.UserService;
import com.stock.trading.web.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户登录、登出和当前用户查询接口。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    /**
     * 校验用户凭据并创建服务端会话。
     *
     * @param request 登录请求
     * @param httpRequest HTTP 请求
     * @return 登录结果及当前用户信息
     */
    @PostMapping("/login")
    public ResponseDTO<AuthUserDto> login(@Valid @RequestBody LoginRequestDto request,
                                          HttpServletRequest httpRequest) {
        log.info("用户登录，username={}", request.getUsername());
        UserEntity user = userService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            return ResponseDTO.error("用户名或密码错误");
        }
        AuthUserDto authUser = userService.toDto(user);
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(AuthInterceptor.AUTH_USER_ID_SESSION_KEY, authUser.getId());
        session.setAttribute(AuthInterceptor.AUTH_USERNAME_SESSION_KEY, authUser.getUsername());
        session.setAttribute(AuthInterceptor.AUTH_DISPLAY_NAME_SESSION_KEY, authUser.getDisplayName());
        return ResponseDTO.success(authUser);
    }

    /**
     * 查询当前服务端会话中的登录用户。
     *
     * @param request HTTP 请求
     * @return 当前用户信息；未登录时返回错误响应
     */
    @GetMapping("/me")
    public ResponseDTO<AuthUserDto> me(HttpServletRequest request) {
        log.info("查询当前登录用户");
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(AuthInterceptor.AUTH_USER_ID_SESSION_KEY) instanceof Long userId
                && session.getAttribute(AuthInterceptor.AUTH_USERNAME_SESSION_KEY) instanceof String username) {
            Object displayName = session.getAttribute(AuthInterceptor.AUTH_DISPLAY_NAME_SESSION_KEY);
            return ResponseDTO.success(AuthUserDto.builder()
                    .id(userId)
                    .username(username)
                    .displayName(displayName instanceof String value ? value : username)
                    .build());
        }
        return ResponseDTO.error("未登录");
    }

    /**
     * 注销当前用户并使服务端会话失效。
     *
     * @param request HTTP 请求
     * @return 注销结果
     */
    @PostMapping("/logout")
    public ResponseDTO<Void> logout(HttpServletRequest request) {
        log.info("用户退出登录");
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseDTO.success(null);
    }
}
// AI_GENERATE_END-------
