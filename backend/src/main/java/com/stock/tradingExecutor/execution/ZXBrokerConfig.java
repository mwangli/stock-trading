package com.stock.tradingExecutor.execution;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 中信证券配置
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
    private String mobileCode = "13278828091";

    /**
     * 账号
     */
    private String account = "880008900626";

    /**
     * 加密密码Redis键名
     */
    private String encodedPasswordKey = "ENCODE_ACCOUNT_PASSWORD";

    /**
     * 签名密钥
     */
    private String signKey = "51cfce1626c7cb087b940a0c224f2caa";

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
