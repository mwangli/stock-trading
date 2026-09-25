// AI_GENERATE_START ---
package com.stock.strategyAnalysis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * T+1 回测基准配置。
 * 费率均为可替换假设，生产回测必须使用真实券商交割单生成新的费用版本。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Component
@ConfigurationProperties(prefix = "backtest")
public class BacktestProperties {

    /** 单次日线基准最多加载的股票数量。 */
    private int universeLimit = 500;

    /** 动量或反转基准默认回看交易日数量。 */
    private int lookbackDays = 5;

    /** 随机基准的可重复随机种子。 */
    private long randomSeed = 20260925L;

    /** 券商佣金率。 */
    private BigDecimal commissionRate = new BigDecimal("0.0005");

    /** 单边最低佣金，单位为元。 */
    private BigDecimal minimumCommission = new BigDecimal("5");

    /** 卖出证券交易印花税率。 */
    private BigDecimal stampDutyRate = new BigDecimal("0.0005");

    /** 双边其他交易费用率，未确认前默认为零。 */
    private BigDecimal transferFeeRate = BigDecimal.ZERO;

    /** 单边滑点，单位为基点。 */
    private BigDecimal slippageBps = new BigDecimal("5");

    /** 无历史板块和 ST 状态时使用的保守涨跌停比例。 */
    private BigDecimal fallbackPriceLimitRate = new BigDecimal("0.10");

    /** 当前费用假设版本。 */
    private String feeVersion = "CN_A_SHARE_ASSUMPTION_2026-09-25";

    /** 当前代码版本，由部署环境覆盖。 */
    private String codeVersion = "WORKTREE_UNCOMMITTED";
}
// AI_GENERATE_END ---
