// AI_GENERATE_START----
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 股票 K 线图表载荷。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDataPayloadDto {

    /** 交易日期列表，格式为 yyyy-MM-dd。 */
    private List<String> dates;

    /** K 线数据，单项依次表示开盘价、收盘价、最低价和最高价。 */
    private List<List<BigDecimal>> kline;

    /** 与交易日期顺序一致的成交量列表。 */
    private List<BigDecimal> volumes;
}
// AI_GENERATE_END----
