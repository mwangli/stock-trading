package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知 DTO (Mock)
 *
 * 当前接口返回空列表，预留结构以便后续扩展。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeDto {

    private String id;

    private String title;

    private String description;

    private boolean read;

    private LocalDateTime time;
}

