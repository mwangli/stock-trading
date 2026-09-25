// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 当日真实交易候选。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class TradingCandidateDto {

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** LSTM 预测得分。 */
    private double lstmScore;

    /** 新闻情感得分。 */
    private double sentimentScore;

    /** 综合得分。 */
    private double totalScore;

    /** 当日候选排名。 */
    private int rank;

    /** 候选产生原因。 */
    private String reason;

    /** 当前账户是否已经持有该股票。 */
    private boolean held;
}
// AI_GENERATE_END -
