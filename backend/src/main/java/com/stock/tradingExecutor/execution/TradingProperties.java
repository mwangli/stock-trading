// AI_GENERATE_START -----
package com.stock.tradingExecutor.execution;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 真实交易运行配置。
 * 只保留人工确认和无人值守两种真实交易方式，所有写操作继续受独立总门禁保护。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    /** 当前真实交易方式，默认每笔人工确认。 */
    private TradingMode mode = TradingMode.LIVE_MANUAL;

    /** 真实下单和撤单总门禁，默认关闭。 */
    private boolean realWriteEnabled = false;

    /** 五年历史同步中相邻月份之间的请求间隔，单位为毫秒。 */
    private int historyMonthIntervalMs = 500;

    /** 无人值守模式下单只候选的默认买入金额。 */
    private BigDecimal autoBuyAmount = BigDecimal.valueOf(1000);

    /** 无人值守模式每次最多买入的候选数量。 */
    private int autoCandidateLimit = 1;

    /** 无人值守模式止盈比例，单位为百分比。 */
    private double takeProfitPercent = 3D;

    /** 无人值守模式对 T+1 持仓执行尾盘退出的时间。 */
    private LocalTime forceExitTime = LocalTime.of(14, 50);

    /**
     * 校验是否允许调用真实券商写接口。
     *
     * @return 显式开启真实写入门禁时返回 true
     */
    public boolean isLiveWriteAllowed() {
        return realWriteEnabled;
    }
}
// AI_GENERATE_END -----
