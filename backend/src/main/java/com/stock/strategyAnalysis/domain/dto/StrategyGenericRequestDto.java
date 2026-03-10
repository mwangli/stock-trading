package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 策略通用请求 DTO
 * <p>
 * 用于 create、update、choose 等 Mock 接口，替代 {@code Map<String, Object>} 入参。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyGenericRequestDto {

    /**
     * 策略 ID
     */
    private String id;

    /**
     * 策略名称
     */
    private String name;

    /**
     * 其他扩展参数（Mock 场景下可空）
     */
    private Map<String, Object> extra;
}
