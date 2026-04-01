package com.stock.tradingExecutor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史订单分页查询请求参数
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryOrderPageRequest {

    /**
     * 关键字（模糊匹配股票代码和名称）
     */
    private String keyword;

    /**
     * 买卖方向：B=买入，S=卖出
     */
    private String direction;

    /**
     * 委托日期开始，格式 yyyyMMdd
     */
    private String startDate;

    /**
     * 委托日期结束，格式 yyyyMMdd
     */
    private String endDate;

    /**
     * 页码（从0开始）
     */
    @Builder.Default
    private Integer page = 0;

    /**
     * 每页大小
     */
    @Builder.Default
    private Integer size = 20;
}