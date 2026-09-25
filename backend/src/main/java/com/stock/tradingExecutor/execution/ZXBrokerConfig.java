// AI_GENERATE_START ---
package com.stock.tradingExecutor.execution;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 中信证券配置
 * 统一承载券商协议所需参数，真实值必须通过环境变量或外部配置注入。
 *
 * @author mwangli
 * @since 2026-03-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "zxbroker")
public class ZXBrokerConfig {

    /**
     * 是否启用
     */
    private Boolean enabled = false;

    /**
     * 手机号
     */
    private String mobileCode = "";

    /**
     * 账号
     */
    private String account = "";

    /**
     * 加密密码配置键名
     */
    private String encodedPasswordKey = "ENCODE_ACCOUNT_PASSWORD";

    /**
     * 签名密钥
     */
    private String signKey = "";

    /**
     * 中信协议完整性参数
     */
    private String intactToServer = "";

    /**
     * Token过期时间(分钟)
     */
    private Integer tokenExpireMinutes = 30;

    /**
     * 登录重试次数
     */
    private Integer loginRetryTimes = 10;

    /**
     * 订单查询最大数量
     */
    private Integer maxOrderCount = 100;

    /**
     * 历史委托和成交单页查询数量
     */
    private Integer historyPageSize = 500;

    /**
     * 单个历史月份最多接收的记录数
     */
    private Integer historyMaxRecordsPerWindow = 5000;

    /**
     * 历史分页请求间隔(毫秒)
     */
    private Integer historyRequestIntervalMs = 200;

    /**
     * 订单等待超时次数
     */
    private Integer orderWaitTimes = 18;

    /**
     * 订单等待间隔(秒)
     */
    private Integer orderWaitInterval = 10;

    /**
     * 撤单等待次数
     */
    private Integer cancelWaitTimes = 6;
}
// AI_GENERATE_END ---
