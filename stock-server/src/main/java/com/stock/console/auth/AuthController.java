// AI_GENERATE_START -
package com.stock.console.auth;

import com.stock.console.config.ConsoleProperties;
import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ConsoleProperties properties;

    public AuthController(ConsoleProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login() {
        return currentUser();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return currentUser();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }

    private ApiResponse<Map<String, Object>> currentUser() {
        String username = properties.auth().username();
        return ApiResponse.ok(Map.of(
                "id", 1,
                "username", username,
                "displayName", username));
    }
}
// AI_GENERATE_END -
