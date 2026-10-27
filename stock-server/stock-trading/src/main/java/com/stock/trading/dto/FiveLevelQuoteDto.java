// AI_GENERATE_START -
package com.stock.trading.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商五档行情快照。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Data
@Builder
public class FiveLevelQuoteDto {
    /** 股票代码。 */
    private String stockCode;
    /** 买一价，单位为元。 */
    private BigDecimal bid1;
    /** 买二价，单位为元。 */
    private BigDecimal bid2;
    /** 买三价，单位为元。 */
    private BigDecimal bid3;
    /** 买四价，单位为元。 */
    private BigDecimal bid4;
    /** 买五价，单位为元。 */
    private BigDecimal bid5;
    /** 卖一价，单位为元。 */
    private BigDecimal ask1;
    /** 卖二价，单位为元。 */
    private BigDecimal ask2;
    /** 卖三价，单位为元。 */
    private BigDecimal ask3;
    /** 卖四价，单位为元。 */
    private BigDecimal ask4;
    /** 卖五价，单位为元。 */
    private BigDecimal ask5;
    /** 券商行情时间；协议未返回时使用接收时间。 */
    private LocalDateTime quoteTime;
}
// AI_GENERATE_END -
