// AI_GENERATE_START ---
package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市场基础数据批量导入结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataImportResultDto {

    /** 导入数据类型。 */
    private String dataType;

    /** 数据来源。 */
    private String source;

    /** 数据源版本或批次。 */
    private String sourceVersion;

    /** 接收到的记录数量。 */
    private int receivedCount;

    /** 已提交幂等新增或更新处理的去重后记录数量，不区分新增和覆盖更新。 */
    private int upsertedCount;

    /** 因同一批次重复键被去重跳过的记录数量。 */
    private int skippedCount;
}
// AI_GENERATE_END ---
