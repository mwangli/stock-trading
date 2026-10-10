package com.stock.tradingExecutor.domain.dto;

import com.stock.tradingExecutor.domain.vo.OrderResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 订单列表分页响应 DTO
 *
 * 对应 /api/orderInfo/list 接口返回结构，替代 Map 形式。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponseDto {

    /**
     * 订单列表
     */
    private List<OrderResult> data;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 当前页码
     */
    private int current;

    /**
     * 每页条数
     */
    private int pageSize;
}

