package com.stock.dataCollector.api;

import com.stock.dataCollector.domain.dto.NewsCollectResponseDto;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.dataCollector.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 新闻采集接口
 * 提供手动触发新闻采集的 REST API
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Slf4j
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final StockNewsService stockNewsService;

    /**
     * 采集指定股票的新闻
     *
     * @param stockCode 股票代码，如 600519
     * @return 采集结果
     */
    @PostMapping("/collect/{stockCode}")
    public ResponseEntity<ResponseDTO<NewsCollectResponseDto>> collectByStockCode(
            @PathVariable String stockCode) {
        log.info("[News] 手动触发单只股票新闻采集 | stockCode={}", stockCode);
        try {
            int savedCount = stockNewsService.collectStockNewsByCode(stockCode);
            NewsCollectResponseDto data = NewsCollectResponseDto.builder()
                    .success(true)
                    .message("采集完成")
                    .processedCount(1)
                    .savedCount(savedCount)
                    .failedCount(0)
                    .build();
            return ResponseEntity.ok(ResponseDTO.success(data));
        } catch (Exception e) {
            log.error("[News] 股票 {} 新闻采集失败", stockCode, e);
            NewsCollectResponseDto data = NewsCollectResponseDto.builder()
                    .success(false)
                    .message(e.getMessage())
                    .processedCount(1)
                    .savedCount(0)
                    .failedCount(1)
                    .build();
            return ResponseEntity.ok(ResponseDTO.<NewsCollectResponseDto>builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(data)
                    .build());
        }
    }

    /**
     * 批量采集所有股票新闻
     *
     * @param maxStocks 可选，最多采集的股票数量，0 或不传表示不限制
     * @return 采集结果
     */
    @PostMapping("/collect")
    public ResponseEntity<ResponseDTO<NewsCollectResponseDto>> collectAll(
            @RequestParam(defaultValue = "0") int maxStocks) {
        log.info("[News] 手动触发批量新闻采集 | maxStocks={}", maxStocks);
        try {
            StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(maxStocks);
            NewsCollectResponseDto data = NewsCollectResponseDto.builder()
                    .success(true)
                    .message("采集完成")
                    .processedCount(result.processedCount())
                    .savedCount(result.savedCount())
                    .failedCount(result.failedCount())
                    .build();
            return ResponseEntity.ok(ResponseDTO.success(data));
        } catch (Exception e) {
            log.error("[News] 批量新闻采集失败", e);
            return ResponseEntity.ok(ResponseDTO.error(e.getMessage()));
        }
    }
}
