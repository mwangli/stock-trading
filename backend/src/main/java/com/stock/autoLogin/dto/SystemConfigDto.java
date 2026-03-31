package com.stock.autoLogin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统配置 DTO
 * <p>
 * 用于接收前端设置页面的配置参数，包括平台 API 密钥等敏感信息。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigDto {

    /**
     * 平台登录 API 密钥
     */
    private String apiKey;

    /**
     * 主题模式：dark/light
     */
    private String theme;

    /**
     * 语言：en/zh
     */
    private String language;

    /**
     * 是否启用系统通知
     */
    private Boolean notifications;

    /**
     * 刷新率（毫秒）
     */
    private Integer refreshRate;

    /**
     * 全局风险概况：conservative/moderate/aggressive
     */
    private String riskLevel;

    /**
     * 最大单日回撤百分比
     */
    private Integer maxDrawdown;
}
