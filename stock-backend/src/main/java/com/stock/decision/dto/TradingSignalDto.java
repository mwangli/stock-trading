// AI_GENERATE_START--------
package com.stock.decision.dto;

import com.stock.decision.entity.Signal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交易信号 DTO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingSignalDto {

    /**
     * 交易信号唯一标识。
     */
    private String signalId;
    /**
     * 股票代码。
     */
    private String stockCode;
    /**
     * 股票名称。
     */
    private String stockName;
    /**
     * 交易信号类型。
     */
    private Signal signalType;
    /**
     * 交易信号强度。
     */
    private int strength;
    /**
     * 情感分类置信度，取值范围为 0 到 1。
     */
    private double confidence;
    /**
     * 生成交易信号的业务原因。
     */
    private String reason;
    /**
     * 交易信号生成时间。
     */
    private LocalDateTime generateTime;
}
// AI_GENERATE_END--------
