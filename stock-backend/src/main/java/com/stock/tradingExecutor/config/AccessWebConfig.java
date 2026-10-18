// AI_GENERATE_START --
package com.stock.tradingExecutor.config;

import com.stock.tradingExecutor.web.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 访问控制 Web 配置。
 * 统一注册登录态拦截器及认证接口白名单。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Configuration
@RequiredArgsConstructor
public class AccessWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 注册业务接口登录态拦截规则。
     *
     * @param registry Spring MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/me",
                        "/api/auth/logout",
                        "/api/system/health");
    }
}
// AI_GENERATE_END --
