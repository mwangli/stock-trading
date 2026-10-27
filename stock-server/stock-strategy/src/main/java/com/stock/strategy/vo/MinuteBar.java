// AI_GENERATE_START--------
package com.stock.strategy.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分钟 K 线 VO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteBar {

    /**
     * 股票代码。
     */
    private String stockCode;
    /**
     * 分钟 K 线对应时间。
     */
    private LocalDateTime time;
    /**
     * 开盘价格，单位为元。
     */
    private double openPrice;
    /**
     * 最高价格，单位为元。
     */
    private double highPrice;
    /**
     * 最低价格，单位为元。
     */
    private double lowPrice;
    /**
     * 收盘价格，单位为元。
     */
    private double closePrice;
    /**
     * 成交量，单位为股。
     */
    private long volume;
    /**
     * 成交额，单位为元。
     */
    private double amount;
}
// AI_GENERATE_END--------
