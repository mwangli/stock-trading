package online.mwang.stockTrading.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.io.Serializable;

/**
 * WebSocket 配置
 */
@Configuration
public class WebSocketConfig implements Serializable {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
