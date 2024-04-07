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

    @Value("${PROFILE}")
    private String profile;

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

    private static final String[] AUTH_URL = new String[]{
            "/job/run",
            "/job/pause",
            "/job/modify",
            "/job/interrupt",
            "/job/resume",
            "/job/create",
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 生产环境需要登录，开发环境不用登录
//        if (profile.equalsIgnoreCase("prod") ) {
        registry.addInterceptor(loginInterceptor).excludePathPatterns(IGNORE_URLS);
        registry.addInterceptor(authInterceptor).addPathPatterns(AUTH_URL);
//        }
    }
}
