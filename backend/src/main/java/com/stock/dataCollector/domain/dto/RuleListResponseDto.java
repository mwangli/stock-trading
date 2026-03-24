package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 规则列表响应 DTO (Mock)
 *
 * 当前仅用于满足前端表格展示需求。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleListResponseDto {

    /**
     * 规则列表数据
     */
    private List<RuleListItemDto> data;

    /**
     * 总条数
     */
    private long total;

    /**
     * 是否成功
     */
    private boolean success;
}

