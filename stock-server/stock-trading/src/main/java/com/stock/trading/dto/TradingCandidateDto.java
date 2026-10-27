// AI_GENERATE_START-------
package com.stock.trading.dto;

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

    /** 融合后的生产有效模型得分。 */
    private Double effectiveModelScore;

    /** 扣除费用和安全边际后的预估净收益。 */
    private Double effectiveEstimatedNetReturn;

    /** 当日候选排名。 */
    private int rank;

    /** 当前账户是否已经持有该股票。 */
    private boolean held;
}
// AI_GENERATE_END-------
