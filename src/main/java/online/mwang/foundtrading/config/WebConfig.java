package online.mwang.foundtrading.config;

import online.mwang.foundtrading.interceptor.LoginInterceptor;
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

    private static final String[] IGNORE_URLS = new String[]{
            "/test",
            "/imageUpdate",
            "/login/account"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(loginInterceptor).excludePathPatterns(IGNORE_URLS);
    }
}
