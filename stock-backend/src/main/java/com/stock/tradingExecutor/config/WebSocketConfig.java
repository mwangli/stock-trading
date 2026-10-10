// AI_GENERATE_START -
package com.stock.tradingExecutor.config;

import com.stock.tradingExecutor.handler.LogWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 运行日志 WebSocket 配置。
 * 仅保留日志查看端点，不提供业务通知端点。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final LogWebSocketHandler logWebSocketHandler;

    /**
     * 注册运行日志查看端点。
     *
     * @param registry WebSocket 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(logWebSocketHandler, "/ws/logs")
                .setAllowedOrigins("*");
    }
}
// AI_GENERATE_END -
