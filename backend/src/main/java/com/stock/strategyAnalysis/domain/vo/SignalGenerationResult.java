package com.stock.strategyAnalysis.domain.vo;

import com.stock.strategyAnalysis.domain.dto.TradingSignalDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信号生成结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalGenerationResult {

    private LocalDateTime generateTime;
    private List<TradingSignalDto> buySignals;
    private List<TradingSignalDto> sellSignals;
    private int buyCount;
    private int sellCount;
    private long costTimeMs;
    private boolean success;
    private String errorMessage;
}
