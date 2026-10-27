// AI_GENERATE_START -
package com.stock.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 使用的模型运维查询结果。
 *
 * @param mode 查询模式
 * @param total 匹配总数
 * @param items 有限模型事实摘要
 * @param summary 查询结论
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-25
 */
public record InternalModelOperationsResponse(
        String mode,
        long total,
        List<String> items,
        String summary,
        LocalDateTime asOf) {
}
// AI_GENERATE_END -
