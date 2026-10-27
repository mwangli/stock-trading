// AI_GENERATE_START------------
package com.stock.data.controller;

import com.stock.base.web.ResponseDTO;
import com.stock.data.dto.HistoryPriceListResponseDto;
import com.stock.data.dto.StockInfoListResponseDto;
import com.stock.data.dto.TopIncreaseListResponseDto;
import com.stock.data.service.StockPriceService;
import com.stock.data.service.StockQueryService;
import com.stock.data.dto.MarketStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 股票信息查询控制器。
 *
 * 面向前端市场和股票信息页面提供分页、历史价格、涨幅榜和市场统计接口。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/stockInfo")
@RequiredArgsConstructor
public class StockInfoController {

    private final StockQueryService stockQueryService;
    private final StockPriceService stockPriceService;

    /**
     * 分页查询可交易股票列表。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页记录数
     * @param keywords 股票代码或名称关键字
     * @param sortBy 排序字段
     * @param sortOrder 排序方向
     * @return 股票分页数据
     */
    @GetMapping("/list")
    public ResponseDTO<StockInfoListResponseDto> listStockInfo(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywords,
            @RequestParam(defaultValue = "updateTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        log.info("[StockInfo] 分页查询股票 | current={}, pageSize={}, keywords={}, sortBy={}, sortOrder={}",
                current, pageSize, keywords, sortBy, sortOrder);
        return ResponseDTO.success(stockQueryService.queryStockInfoPage(
                current, pageSize, keywords, sortBy, sortOrder));
    }

    /**
     * 查询指定股票的供应商历史日线。
     *
     * @param code 股票代码
     * @return 历史日线列表
     */
    @GetMapping("/listHistoryPrices")
    public ResponseDTO<HistoryPriceListResponseDto> listHistoryPrices(@RequestParam String code) {
        log.info("[StockInfo] 获取历史价格 | code={}", code);
        HistoryPriceListResponseDto result = HistoryPriceListResponseDto.builder()
                .items(stockPriceService.getHistoryPriceItems(code))
                .build();
        return ResponseDTO.success(result);
    }

    /**
     * 查询可交易股票涨幅榜前十名。
     *
     * @return 涨幅榜数据
     */
    @GetMapping("/listIncreaseRate")
    public ResponseDTO<TopIncreaseListResponseDto> listIncreaseRate() {
        log.info("[StockInfo] 查询涨幅榜");
        return ResponseDTO.success(stockQueryService.getTopIncreaseList());
    }

    /**
     * 查询当前市场状态和涨跌分布。
     *
     * @return 市场统计数据
     */
    @GetMapping("/marketStats")
    public ResponseDTO<MarketStatsDto> getMarketStats() {
        log.info("[StockInfo] 查询市场统计");
        return ResponseDTO.success(stockQueryService.getMarketStats());
    }
}
// AI_GENERATE_END------------
