package com.stock.databus.scheduled;

import com.stock.databus.client.SecuritiesPlatformClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token 刷新定时任务
 * 每 30 分钟刷新一次 Token，确保接口调用有效
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenJob {

    private final SecuritiesPlatformClient securitiesPlatformClient;

    /**
     * 每 30 分钟刷新一次 Token
     * Token 有效期为 30 分钟，需要提前刷新
     */
    @Scheduled(fixedRate = 1800000) // 30 分钟 = 1800000 毫秒
    public void refreshToken() {
        log.info("开始执行 Token 刷新任务");
        try {
            // 通过调用需要 Token 的接口来获取新的 Token
            // 这里使用获取实时价格的接口作为示例
            // 实际应该使用专门的 Token 刷新接口
            securitiesPlatformClient.getNowPrice("000001");
            log.info("Token 刷新成功");
        } catch (Exception e) {
            log.error("Token 刷新失败", e);
        }
    }
}
