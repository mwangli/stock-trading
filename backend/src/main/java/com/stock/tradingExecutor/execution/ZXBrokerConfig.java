// AI_GENERATE_START ---------
package com.stock.tradingExecutor.execution;

import lombok.Data;
import lombok.ToString;
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
    @ToString.Exclude
    private String mobileCode = "";

    /**
     * 账号
     */
    @ToString.Exclude
    private String account = "";

    /**
     * 券商网页协议使用的加密密码
     */
    @ToString.Exclude
    private String encodedPassword = "";

    /**
     * 签名密钥
     */
    @ToString.Exclude
    private String signKey = "";

    /**
     * 中信协议完整性参数
     */
    private String intactToServer = "";


    /**
     * OCR 最低一致票数
     */
    private Integer ocrMinAgreement = 2;

    /**
     * OCR 远程补票请求超时时间，单位毫秒
     */
    private Integer ocrTimeoutMs = 20000;

    /**
     * 百度 OCR API Key，仅作为本地识别不足时的补票来源
     */
    @ToString.Exclude
    private String baiduOcrApiKey = "";

    /**
     * 百度 OCR Secret Key，仅作为本地识别不足时的补票来源
     */
    @ToString.Exclude
    private String baiduOcrSecretKey = "";

    /**
     * 验证码最大重试次数
     */
    private Integer captchaMaxRetries = 3;

    /**
     * Token过期时间(分钟)
     */
    private Integer tokenExpireMinutes = 30;

    /**
     * Token 在 Redis 中使用的键
     */
    private String tokenRedisKey = "stock-trading:zxbroker:token";

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
// AI_GENERATE_END ---------
