// AI_GENERATE_START -
package com.stock.tradingExecutor.notification;

import com.stock.tradingExecutor.domain.vo.OrderResult;

/**
 * 交易通知扩展接口。
 * 当前不接入具体通知渠道，后续实现可独立替换且不得阻塞交易主流程。
 *
 * @author mwangli
 * @since 2026-10-08
 */
public interface TradeNotificationPort {

    /**
     * 接收订单状态变化。
     *
     * @param result 订单执行结果
     * @param type 买卖方向
     */
    void publishOrderResult(OrderResult result, String type);

    /**
     * 接收交易系统异常。
     *
     * @param title 异常标题
     * @param errorMessage 异常内容
     */
    void publishError(String title, String errorMessage);
}
// AI_GENERATE_END -
