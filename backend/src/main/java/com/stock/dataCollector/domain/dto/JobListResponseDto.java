package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务列表响应 DTO (Mock)
 *
 * 对应 /api/job/list 接口返回。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobListResponseDto {

    private List<JobListItemDto> data;

    private long total;

    private boolean success;
}

