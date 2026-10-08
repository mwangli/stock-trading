// AI_GENERATE_START -
package com.stock.dataCollector.api;

import com.stock.dataCollector.domain.vo.MarketStatsDto;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.domain.dto.StockInfoListResponseDto;
import com.stock.dataCollector.domain.dto.HistoryPriceListResponseDto;
import com.stock.dataCollector.domain.dto.TopIncreaseItemDto;
import com.stock.dataCollector.domain.dto.TopIncreaseListResponseDto;
import com.stock.dataCollector.domain.dto.MarketStatsResponseDto;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.stream.Collectors;

/**
 * 股票信息控制器
 * <p>
 * 对应前端 /api/stockInfo/* 接口，提供股票列表、历史价格、涨幅榜和市场统计。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/stockInfo")
@RequiredArgsConstructor
public class StockInfoController {

    private final StockDataService stockDataService;

    /**
     * 使用数据库分页查询股票列表。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页数量
     * @param keywords 股票名称或代码关键字
     * @return 股票分页结果
     */
    @GetMapping("/list")
    public ResponseEntity<StockInfoListResponseDto> listStockInfo(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords) {
        log.info("[StockInfo] 请求到达 GET /api/stockInfo/list current={} pageSize={} keywords={}", current, pageSize, keywords);

        // 使用数据库分页查询 (Page 0-based, so subtract 1)
        int pageNumber = Math.max(0, current - 1);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "id"));

        // 构建动态查询条件，使用 keywords 同时匹配 name 或 code
        Page<StockInfo> pageResult = stockDataService.findStocksWithKeywords(keywords, pageRequest);

        StockInfoListResponseDto response = StockInfoListResponseDto.builder()
                .data(pageResult.getContent())
                .total(pageResult.getTotalElements())
                .success(true)
                .current(pageResult.getNumber() + 1)
                .pageSize(pageResult.getSize())
                .build();
        log.info("[StockInfo] /list 即将返回 total={}", pageResult.getTotalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * 查询指定股票的历史价格。
     *
     * @param code 股票代码
     * @return 历史价格列表
     */
    @GetMapping("/listHistoryPrices")
    public ResponseEntity<HistoryPriceListResponseDto> listHistoryPrices(@RequestParam String code) {
        log.info("[StockInfo] 获取历史价格 | code={}", code);
        List<StockPrice> prices = stockDataService.getHistoryPrices(code);

        HistoryPriceListResponseDto response = HistoryPriceListResponseDto.builder()
                .data(prices)
                .success(true)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 查询涨幅榜前十名。
     *
     * @return 涨幅榜结果
     */
    @GetMapping("/listIncreaseRate")
    public ResponseEntity<TopIncreaseListResponseDto> listIncreaseRate() {
        log.info("[StockInfo] 请求到达 GET /api/stockInfo/listIncreaseRate");
        List<StockInfo> top10Stocks = stockDataService.findTop10ByIncreaseRate();

        List<TopIncreaseItemDto> top10 = top10Stocks.stream()
                .map(stock -> TopIncreaseItemDto.builder()
                        .code(stock.getCode())
                        .name(stock.getName())
                        .changePercent(stock.getChangePercent())
                        .build())
                .collect(Collectors.toList());

        TopIncreaseListResponseDto response = TopIncreaseListResponseDto.builder()
                .data(top10)
                .success(true)
                .build();
        log.info("[StockInfo] /listIncreaseRate 即将返回 size={}", top10.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 从 stock_info 表聚合市场统计信息。
     *
     * @return 真实市场统计结果
     */
    @GetMapping("/marketStats")
    public ResponseEntity<MarketStatsResponseDto> getMarketStats() {
        log.info("[StockInfo] 请求到达 GET /api/stockInfo/marketStats");
        MarketStatsDto stats = stockDataService.getMarketStats();
        MarketStatsResponseDto response = MarketStatsResponseDto.builder()
                .success(true)
                .data(stats)
                .build();
        log.info("[StockInfo] marketStats 即将返回 success=true");
        return ResponseEntity.ok(response);
    }
}
// AI_GENERATE_END -
