// AI_GENERATE_START --
package com.stock.agent.shared.config;

import com.stock.agent.shared.auth.AgentAuthInterceptor;
import com.stock.agent.shared.auth.InternalTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Agent 服务访问控制配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Configuration
@RequiredArgsConstructor
public class AgentWebConfig implements WebMvcConfigurer {

    private final AgentAuthInterceptor authInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    /**
     * 注册共享登录态和内部令牌拦截规则。
     *
     * @param registry Spring MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalTokenInterceptor).addPathPatterns("/api/internal/**");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/internal/**", "/api/agent/runtime/health");
    }
}
// AI_GENERATE_END -
