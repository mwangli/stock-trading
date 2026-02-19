package online.mwang.stockTrading.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 方向：BUY/SELL
     */
    private String direction;

    /**
     * 成交数量
     */
    private int filledQuantity;

    /**
     * 成交价格
     */
    private double filledPrice;

    /**
     * 成交金额
     */
    private double filledAmount;

    /**
     * 手续费
     */
    private double fee;

    /**
     * 错误信息
     */
    private String errorMessage;

    public static OrderResult success(String orderId, String stockCode, String direction, 
                                       int quantity, double price, double fee) {
        return OrderResult.builder()
                .success(true)
                .orderId(orderId)
                .stockCode(stockCode)
                .direction(direction)
                .filledQuantity(quantity)
                .filledPrice(price)
                .filledAmount(price * quantity)
                .fee(fee)
                .build();
    }

    public static OrderResult fail(String errorMessage) {
        return OrderResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
