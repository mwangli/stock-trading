// AI_GENERATE_START
package com.stock.user.config;

import com.stock.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户认证组件配置和默认用户初始化配置。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Configuration
public class UserAuthConfig {

    /** 创建 BCrypt 密码编码器。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 应用启动时幂等初始化默认用户。 */
    @Bean
    public CommandLineRunner defaultUserInitializer(UserService userService) {
        return args -> {
            userService.initializeDefaultUser("admin", "管理员");
            userService.initializeDefaultUser("test", "测试用户");
        };
    }
}
// AI_GENERATE_END
