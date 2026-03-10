package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通知列表响应 DTO
 *
 * 对应 /api/login/../notices 接口返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeListResponseDto {

    private List<NoticeDto> data;

    private long total;

    private boolean success;
}

