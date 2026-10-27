// AI_GENERATE_START- -
package com.stock.data.dto;

import java.time.LocalDateTime;

/**
 * 外部新闻来源解析后的统一采集记录。
 *
 * @param sourceKey 来源稳定标识，不可为空
 * @param externalId 来源侧唯一标识，不可为空
 * @param title 原始标题，不可为空
 * @param content 原始正文或摘要，可为空
 * @param stockCode 关联股票代码，不可为空
 * @param stockName 关联股票名称，可为空
 * @param source 来源媒体或监管机构，不可为空
 * @param channel 来源栏目，可为空
 * @param sourceUrl 原始页面地址，可为空
 * @param publishTime 发布时间，不可为空
 * @author mwangli
 * @since 2026-10-24
 */
public record ExternalNewsRecord(
        String sourceKey,
        String externalId,
        String title,
        String content,
        String stockCode,
        String stockName,
        String source,
        String channel,
        String sourceUrl,
        LocalDateTime publishTime) {
}
// AI_GENERATE_END- -
