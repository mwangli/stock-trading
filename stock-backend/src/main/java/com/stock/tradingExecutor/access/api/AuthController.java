// AI_GENERATE_START --
package com.stock.tradingExecutor.access.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.access.domain.dto.AuthUserDto;
import com.stock.tradingExecutor.access.domain.dto.LoginRequestDto;
import com.stock.tradingExecutor.access.domain.entity.UserEntity;
import com.stock.tradingExecutor.access.service.UserService;
import com.stock.tradingExecutor.access.web.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
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
        httpRequest.getSession(true).setAttribute(AuthInterceptor.AUTH_USER_SESSION_KEY, authUser);
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
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(AuthInterceptor.AUTH_USER_SESSION_KEY);
        if (user instanceof AuthUserDto authUser) {
            return ResponseDTO.success(authUser);
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
// AI_GENERATE_END --
