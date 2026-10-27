// AI_GENERATE_START--------
package com.stock.trading.vo;

import com.stock.trading.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单结果
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
public class OrderResult {

    /**
     * 业务处理是否成功。
     */
    private boolean success;
    /**
     * 订单唯一标识。
     */
    private String orderId;
    /**
     * 股票代码。
     */
    private String stockCode;
    /**
     * 股票名称。
     */
    private String stockName;
    /**
     * 监控的交易方向。
     */
    private String direction;
    /**
     * 委托或成交价格，单位为元。
     */
    private BigDecimal price;
    /**
     * 委托或成交数量，单位为股。
     */
    private Integer quantity;
    /**
     * 成交额，单位为元。
     */
    private BigDecimal amount;
    /**
     * 交易费用，单位为元。
     */
    private BigDecimal fee;
    /**
     * 订单当前状态。
     */
    private OrderStatus status;
    /**
     * 订单处理结果说明。
     */
    private String message;
    /**
     * 订单提交时间。
     */
    private LocalDateTime submitTime;
    /**
     * 订单成交时间，未成交时可为空。
     */
    private LocalDateTime fillTime;

    /**
     * 创建不包含规则明细的失败结果。
     *
     * @param message 失败原因
     * @return 失败订单结果
     */
    public static OrderResult fail(String message) {
        return OrderResult.builder()
                .success(false)
                .message(message)
                .submitTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建包含风控规则明细的失败结果。
     *
     * @param message 失败原因
     * @param violations 风控违规明细
     * @return 失败订单结果
     */
    public static OrderResult fail(String message, List<String> violations) {
        return OrderResult.builder()
                .success(false)
                .message(message + ": " + String.join(", ", violations))
                .submitTime(LocalDateTime.now())
                .build();
    }
}

// AI_GENERATE_END--------
