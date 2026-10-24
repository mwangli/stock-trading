// AI_GENERATE_START--------
package com.stock.decision.vo;

import com.stock.decision.dto.StockRankingDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 选股结果 VO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectionResult {

    /**
     * 选股任务执行时间。
     */
    private LocalDateTime executeTime;
    /**
     * 参与选股的股票总数，单位为只。
     */
    private int totalStocks;
    /**
     * 按策略评分筛选出的股票排名列表。
     */
    private List<StockRankingDto> topN;
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
// AI_GENERATE_END--------
