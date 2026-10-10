package com.stock.strategyAnalysis.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分钟 K 线 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteBar {

    private String stockCode;
    private LocalDateTime time;
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double closePrice;
    private long volume;
    private double amount;
}
