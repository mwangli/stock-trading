// AI_GENERATE_START--------
package com.stock.agent.tool;

import com.stock.agent.service.AgentFactQueryService;
import com.stock.agent.model.LatestPriceFact;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票最新日线价格只读工具。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
@RequiredArgsConstructor
public class LatestPriceQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定股票在本地行情库中的最新日线价格。
     *
     * @param request 股票代码请求
     * @return 最新价格及数据日期
     */
    @Tool(name = "queryLatestPrice", value = "查询指定股票在本地行情库中的最新日线价格和数据日期")
    public LatestPriceResult query(LatestPriceRequest request) {
        if (request == null || !StringUtils.hasText(request.stockCode())) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        String stockCode = request.stockCode().trim();
        return toolSupport.execute("queryLatestPrice", () -> {
            LatestPriceFact price = factQueryService.latestPrice(stockCode);
            return new LatestPriceResult(price.stockCode(), price.stockName(), price.tradeDate(),
                    price.openPrice(), price.highPrice(), price.lowPrice(), price.closePrice(),
                    price.volume(), price.amount(), price.updatedAt());
        });
    }

    /** 股票最新价格查询请求。 */
    public record LatestPriceRequest(String stockCode) {
    }

    /** 股票最新价格查询结果。 */
    public record LatestPriceResult(String stockCode, String stockName, LocalDate tradeDate,
                                    BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
                                    BigDecimal closePrice, BigDecimal volume, BigDecimal amount,
                                    LocalDateTime updatedAt) {
    }
}
// AI_GENERATE_END-------