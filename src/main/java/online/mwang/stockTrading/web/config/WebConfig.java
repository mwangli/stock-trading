package online.mwang.stockTrading.web.config;

import online.mwang.stockTrading.web.interceptor.AuthInterceptor;
import online.mwang.stockTrading.web.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author 13255
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {


    @Resource
    private LoginInterceptor loginInterceptor;
    @Resource
    private AuthInterceptor authInterceptor;

    // 无需认证URL
    private static final String[] IGNORE_URLS = new String[]{
            "/test",
            "/imageUpdate",
            "/login/account",
            "/**/test**",
            "/**/**/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/v2/**",
            "/swagger-ui.html/**"
    };

    // 需要授权URL
    private static final String[] AUTH_URL = new String[]{
            // 任务操作
            "/job/run",
            "/job/pause",
            "/job/interrupt",
            "/job/resume",
            // 资源修改操作
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
