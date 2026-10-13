// AI_GENERATE_START --
package com.stock.tradingExecutor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 前端静态资源配置。
 * 将未知页面路径转发到前端 index.html，支持 React Router history 模式。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册前端静态资源及单页应用回退规则。
     *
     * @param registry 静态资源处理器注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
// AI_GENERATE_END --
