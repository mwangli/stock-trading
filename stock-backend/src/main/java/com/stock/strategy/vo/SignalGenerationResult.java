// AI_GENERATE_START-------
package com.stock.strategy.vo;

import com.stock.strategy.dto.TradingSignalDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信号生成结果 VO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalGenerationResult {

    /**
     * 交易信号生成时间。
     */
    private LocalDateTime generateTime;
    /**
     * 本次生成的买入信号列表。
     */
    private List<TradingSignalDto> buySignals;
    /**
     * 本次生成的卖出信号列表。
     */
    private List<TradingSignalDto> sellSignals;
    /**
     * 买入信号数量。
     */
    private int buyCount;
    /**
     * 卖出信号数量。
     */
    private int sellCount;
    /**
     * 选股任务耗时，单位为毫秒。
     */
    private long costTimeMs;
    /**
     * 业务处理是否成功。
     */
    private boolean success;
    /**
     * 业务处理失败时的错误信息，成功时可为空。
     */
    private String errorMessage;
}
// AI_GENERATE_END-------
