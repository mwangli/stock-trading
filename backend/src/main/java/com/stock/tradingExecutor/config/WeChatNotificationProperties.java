// AI_GENERATE_START -
package com.stock.tradingExecutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序订阅消息配置。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Component
@ConfigurationProperties(prefix = "notification.wechat")
public class WeChatNotificationProperties {

    /** 是否启用微信小程序订阅消息。 */
    private boolean enabled = false;

    /** 微信小程序 AppId。 */
    private String appId;

    /** 微信小程序 AppSecret。 */
    private String appSecret;

    /** 接收通知的本人 OpenId。 */
    private String openId;

    /** 已申请的订阅消息模板 ID。 */
    private String templateId;

    /** 点击通知后打开的小程序页面。 */
    private String page = "pages/index/index";

    /** 模板中的标题字段名。 */
    private String titleField = "thing1";

    /** 模板中的内容字段名。 */
    private String contentField = "thing2";
}
// AI_GENERATE_END -
