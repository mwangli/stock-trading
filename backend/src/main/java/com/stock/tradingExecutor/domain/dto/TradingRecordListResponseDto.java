package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 交易记录列表分页响应 DTO
 *
 * 对应 /api/tradingRecord/list 接口返回。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingRecordListResponseDto {

    private List<TradingRecordDto> data;

    private long total;

    private boolean success;

    private int current;

    private int pageSize;
}

