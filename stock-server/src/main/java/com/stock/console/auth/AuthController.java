// AI_GENERATE_START -
package com.stock.console.auth;

import com.stock.console.config.ConsoleProperties;
import com.stock.console.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SESSION_USER = "consoleUser";
    private final ConsoleProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(ConsoleProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request) {
        ConsoleProperties.Auth auth = properties.auth();
        if (auth.passwordHash() == null || auth.passwordHash().isBlank()) {
            throw new IllegalStateException("CONSOLE_PASSWORD_HASH 未配置");
        }
        if (!auth.username().equals(login.username())
                || !passwordEncoder.matches(login.password(), auth.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        request.getSession(true).setAttribute(SESSION_USER, auth.username());
        return ApiResponse.ok(Map.of("username", auth.username()));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, String>> me(HttpSession session) {
        return ApiResponse.ok(Map.of("username", String.valueOf(session.getAttribute(SESSION_USER))));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(null);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
// AI_GENERATE_END -
