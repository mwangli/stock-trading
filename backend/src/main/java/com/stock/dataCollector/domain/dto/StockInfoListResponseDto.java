package com.stock.dataCollector.domain.dto;

import com.stock.dataCollector.domain.entity.StockInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 股票列表分页响应 DTO
 *
 * 封装 /api/stockInfo/list 接口返回的列表数据、分页信息和成功标记，
 * 以替代 Controller 层直接返回 Map 的做法。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfoListResponseDto {

    /**
     * 股票列表数据
     */
    private List<StockInfo> data;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 当前页码（从 1 开始）
     */
    private int current;

    /**
     * 每页条数
     */
    private int pageSize;
}

