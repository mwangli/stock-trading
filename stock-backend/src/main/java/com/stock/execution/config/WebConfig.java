// AI_GENERATE_START---------
package com.stock.execution.config;

import org.springframework.http.CacheControl;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    /**
                    * 优先返回静态资源，未命中时回退到前端入口页面。
                    *
                    * @param resourcePath 请求资源路径
                    * @param location 静态资源根位置
                    * @return 命中的静态资源或前端入口资源
                    * @throws IOException 资源读取失败时抛出
                    */
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("ws/")
                                || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource fallbackResource = location.createRelative("index.html");
                        return fallbackResource.exists() && fallbackResource.isReadable()
                                ? fallbackResource
                                : null;
                    }
                });
    }
}
// AI_GENERATE_END---------
