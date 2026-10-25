// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 模型训练基础数据审计结果。
 * 汇总指定来源版本下关键集合的股票标识覆盖率和参考序列可用性。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingDataAuditDto {

    /** 模型数据来源。 */
    private String source;

    /** 模型数据来源版本。 */
    private String sourceVersion;

    /** 本次训练要求的股票数量。 */
    private int requestedStockCount;

    /** 已有模型专用日线的股票数量。 */
    private int modelBarStockCount;

    /** 已有复权因子的股票数量。 */
    private int adjustmentFactorStockCount;

    /** 已有历史股票池记录的股票数量。 */
    private int historicalUniverseStockCount;

    /** 已有可交易事实的股票数量。 */
    private int tradabilityStockCount;

    /** 历史股票池中要求的行业参考序列数量。 */
    private int requiredIndustrySeriesCount;

    /** 已有行业参考序列数量。 */
    private int availableIndustrySeriesCount;

    /** 市场基准序列是否存在。 */
    private boolean benchmarkSeriesAvailable;

    /** 当前来源版本的最新模型日线日期。 */
    private LocalDate latestModelBarDate;

    /** 缺少模型日线的股票代码样例，最多二十项。 */
    private List<String> missingModelBarCodes;

    /** 缺少复权因子的股票代码样例，最多二十项。 */
    private List<String> missingAdjustmentFactorCodes;

    /** 缺少历史股票池的股票代码样例，最多二十项。 */
    private List<String> missingHistoricalUniverseCodes;

    /** 缺少可交易事实的股票代码样例，最多二十项。 */
    private List<String> missingTradabilityCodes;

    /** 缺少参考日线的行业代码样例，最多二十项。 */
    private List<String> missingIndustrySeriesCodes;
}
// AI_GENERATE_END -
