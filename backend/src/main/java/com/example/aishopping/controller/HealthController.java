package com.example.aishopping.controller;

import com.example.aishopping.websocket.CollectionLogWebSocket;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "AI Shopping Backend");
        result.put("version", "1.0.0");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    @GetMapping("/ws-status")
    public Map<String, Object> wsStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("wsEnabled", true);
        result.put("wsEndpoint", "/ws/collection-log/{taskId}");
        result.put("connections", CollectionLogWebSocket.getConnectionCount());
        result.put("message", "WebSocket is available");
        return result;
    }
}
