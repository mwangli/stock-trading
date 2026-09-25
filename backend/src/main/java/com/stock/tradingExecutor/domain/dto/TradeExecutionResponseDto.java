// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import com.stock.tradingExecutor.domain.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 真实委托执行结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class TradeExecutionResponseDto {

    /** 券商是否接受并完成当前执行流程。 */
    private boolean success;

    /** 券商委托编号。 */
    private String orderId;

    /** 股票代码。 */
    private String stockCode;

    /** 买卖方向。 */
    private String direction;

    /** 委托价格。 */
    private BigDecimal price;

    /** 委托数量。 */
    private Integer quantity;

    /** 最终订单状态。 */
    private OrderStatus status;

    /** 执行结果说明。 */
    private String message;

    /** 委托提交时间。 */
    private LocalDateTime submitTime;

    /** 成交时间。 */
    private LocalDateTime fillTime;
}
// AI_GENERATE_END -
