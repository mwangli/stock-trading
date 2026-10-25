// AI_GENERATE_START -
package com.stock.market.client.news;

import com.stock.market.dto.ExternalNewsRecord;
import com.stock.market.entity.StockInfo;

import java.util.List;

/**
 * 免费公开新闻来源统一采集契约。
 * 实现类只负责来源协议、字段解析和股票关联，不直接持久化新闻。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public interface ExternalNewsSourceClient {

    /**
     * 返回来源稳定标识。
     *
     * @return 来源标识
     */
    String sourceKey();

    /**
     * 判断当前来源是否启用。
     *
     * @return 启用时返回 true
     */
    boolean isEnabled();

    /**
     * 从公开来源采集最近记录并完成可靠股票关联。
     *
     * @param stocks 当前股票基础信息，用于媒体和监管文本实体链接
     * @param maxRecords 单次最多返回记录数
     * @return 已关联到具体股票的来源记录
     * @throws Exception HTTP、解析或来源协议异常时抛出
     */
    List<ExternalNewsRecord> fetch(List<StockInfo> stocks, int maxRecords) throws Exception;
}
// AI_GENERATE_END -
