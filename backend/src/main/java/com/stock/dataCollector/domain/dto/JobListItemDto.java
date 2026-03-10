package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务列表单项 DTO (Mock)
 *
 * 对应 /api/job/list 接口中的单个任务数据结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobListItemDto {

    private String id;

    private String name;

    private String cron;

    private String status;

    private String description;
}

