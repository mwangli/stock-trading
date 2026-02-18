package online.mwang.stockTrading.core.config;

import online.mwang.stockTrading.core.interceptor.AuthInterceptor;
import online.mwang.stockTrading.core.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * Web MVC 配置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;
    @Resource
    private AuthInterceptor authInterceptor;

    private static final String[] IGNORE_URLS = new String[]{
            "/test/**",
            "/imageUpdate",
            "/login/account",
            "/**/test**",
            "/**/**/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/v2/**",
            "/swagger-ui.html/**"
    };

    private static final String[] AUTH_URL = new String[]{
            "/job/run",
            "/job/pause",
            "/job/interrupt",
            "/job/resume",
            "/**/create",
            "/**/update",
            "/**/delete",
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor).excludePathPatterns(IGNORE_URLS);
        registry.addInterceptor(authInterceptor).addPathPatterns(AUTH_URL);
    }
}
