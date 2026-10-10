package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 股票 K 线数据响应 DTO
 *
 * 对应 /api/stock-data/kline/{code} 接口返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDataResponseDto {

    private boolean success;

    private String message;

    private KlineDataWrapper data;

    private int total;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KlineDataWrapper {
        private List<String> dates;
        private List<List<Object>> kline;
        private List<Object> volumes;
    }
}

