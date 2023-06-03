package online.mwang.foundtrading.config;
 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.io.Serializable;
 
/**
 * TODO websocket配置
 *
 * @author xiaoshuaishuai
 * @date 2019-05-21 11:18
 */
@Configuration
public class WebSocketConfig implements Serializable {
 
    private static final long serialVersionUID = 7600559593733357846L;

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
 
}