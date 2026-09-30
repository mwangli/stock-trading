// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 券商本地验证码登录结果。
 *
 * @author mwangli
 * @since 2026-09-30
 */
@Data
@Builder
public class BrokerLoginResultDto {

    /**
     * 当前券商会话是否已认证
     */
    private boolean authenticated;

    /**
     * 脱敏后的资金账号
     */
    private String maskedAccount;
}
// AI_GENERATE_END -