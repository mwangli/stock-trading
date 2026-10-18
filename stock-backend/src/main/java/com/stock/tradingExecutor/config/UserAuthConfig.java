// AI_GENERATE_START ---
package com.stock.tradingExecutor.config;

import com.stock.tradingExecutor.service.UserService;
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

    /**
     * 创建 BCrypt 密码编码器。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 创建默认用户幂等初始化任务。
     *
     * @param userService 用户认证服务
     * @return 应用启动任务
     */
    @Bean
    public CommandLineRunner defaultUserInitializer(UserService userService) {
        return args -> {
            userService.initializeDefaultUser("admin", "管理员");
            userService.initializeDefaultUser("test", "测试用户");
        };
    }
}
// AI_GENERATE_END ---
