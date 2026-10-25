// AI_GENERATE_START -
package com.stock.market.dto;

import java.util.List;

/**
 * 外部新闻来源增量采集结果。
 *
 * @param enabled 外部来源总开关是否启用
 * @param configuredSourceCount 已启用的来源数量
 * @param successfulSourceCount 成功来源数量
 * @param fetchedCount 解析并完成股票关联的记录数量
 * @param insertedCount 新增原始新闻数量
 * @param eventFactCount 同步的交易和风险事件文档数量
 * @param errors 来源错误摘要列表
 * @author mwangli
 * @since 2026-10-24
 */
public record ExternalNewsCollectionResult(
        boolean enabled,
        int configuredSourceCount,
        int successfulSourceCount,
        int fetchedCount,
        int insertedCount,
        long eventFactCount,
        List<String> errors) {

    /**
     * 返回总开关关闭时的空结果。
     *
     * @return 未启用外部来源的空结果
     */
    public static ExternalNewsCollectionResult disabled() {
        return new ExternalNewsCollectionResult(false, 0, 0, 0, 0, 0L, List.of());
    }
}
// AI_GENERATE_END -
