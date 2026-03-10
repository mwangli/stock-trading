package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 删除规则请求 DTO
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDeleteRequestDto {

    /**
     * 规则 ID
     */
    private String id;
}
