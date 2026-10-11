// AI_GENERATE_START
package com.stock.user.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.user.domain.dto.AuthUserDto;
import com.stock.user.domain.dto.LoginRequestDto;
import com.stock.user.domain.entity.UserEntity;
import com.stock.user.service.UserService;
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

    /** 用户登录并创建服务端会话。 */
    @PostMapping("/login")
    public ResponseDTO<AuthUserDto> login(@Valid @RequestBody LoginRequestDto request,
                                          HttpServletRequest httpRequest) {
        log.info("用户登录，username={}", request.getUsername());
        UserEntity user = userService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            return ResponseDTO.error("用户名或密码错误");
        }
        httpRequest.getSession(true).setAttribute("AUTH_USER", userService.toDto(user));
        return ResponseDTO.success(userService.toDto(user));
    }

    /** 查询当前会话用户。 */
    @GetMapping("/me")
    public ResponseDTO<AuthUserDto> me(HttpServletRequest request) {
        log.info("查询当前登录用户");
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("AUTH_USER");
        if (user instanceof AuthUserDto authUser) {
            return ResponseDTO.success(authUser);
        }
        return ResponseDTO.error("未登录");
    }

    /** 注销当前会话。 */
    @PostMapping("/logout")
    public ResponseDTO<Void> logout(HttpServletRequest request) {
        log.info("用户退出登录");
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseDTO.success(null);
    }
}
// AI_GENERATE_END
