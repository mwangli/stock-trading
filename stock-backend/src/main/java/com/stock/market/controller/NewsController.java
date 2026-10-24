// AI_GENERATE_START-----
package com.stock.market.controller;

import com.stock.market.dto.NewsCollectResponseDto;
import com.stock.market.dto.NewsSampleResponseDto;
import com.stock.platform.dto.ResponseDTO;
import com.stock.market.entity.StockNews;
import com.stock.market.service.StockNewsService;
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
     * 随机读取一条信息表样本，用于情感模型单条验证。
     *
     * @return 信息表中的随机新闻或公告；没有可用数据时返回空数据
     */
    @GetMapping("/randomSample")
    public ResponseDTO<NewsSampleResponseDto> randomSample() {
        log.info("[News] 随机读取情感分析验证样本");
        return stockNewsService.findRandomNewsSample()
                .map(this::toSampleResponse)
                .map(ResponseDTO::success)
                .orElseGet(() -> ResponseDTO.success(null));
    }

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

    /**
     * 将新闻实体转换为单条验证样本 DTO。
     *
     * @param news 新闻实体
     * @return 单条验证样本
     */
    private NewsSampleResponseDto toSampleResponse(StockNews news) {
        return NewsSampleResponseDto.builder()
                .title(news.getTitle())
                .content(news.getContent())
                .stockCode(news.getStockCode())
                .stockName(news.getStockName())
                .source(news.getSource())
                .category(news.getCategory())
                .publishTime(news.getPublishTime() == null ? null : news.getPublishTime().toString())
                .build();
    }
}
// AI_GENERATE_END-----
