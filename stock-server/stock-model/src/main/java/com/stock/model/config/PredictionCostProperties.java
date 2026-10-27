// AI_GENERATE_START----
package com.stock.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 模型预测候选净收益计算配置。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Component
@ConfigurationProperties(prefix = "backtest")
public class PredictionCostProperties {

    /** 单边佣金率。 */
    private BigDecimal commissionRate = new BigDecimal("0.0005");

    /** 双边其他交易费用率。 */
    private BigDecimal transferFeeRate = BigDecimal.ZERO;

    /** 卖出证券交易印花税率。 */
    private BigDecimal stampDutyRate = new BigDecimal("0.0005");

    /** 单边滑点，单位为基点。 */
    private BigDecimal slippageBps = new BigDecimal("5");

    /** 预测候选的额外安全边际收益率。 */
    private BigDecimal candidateSafetyMarginRate = new BigDecimal("0.0010");
}
// AI_GENERATE_END----
