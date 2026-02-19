package online.mwang.stockTrading.modules.datacollection.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.core.dto.Response;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.service.StockDataService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 股票数据采集控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class StockDataController {

    private final StockDataService stockDataService;

    /**
     * 获取股票列表
     */
    @GetMapping("/stocks")
    public Response<List<StockInfo>> getStockList() {
        log.info("API: Getting stock list");
        List<StockInfo> stockList = stockDataService.fetchStockList();
        return Response.success(stockList);
    }

    /**
     * 获取历史K线数据
     */
    @GetMapping("/stocks/{code}/history")
    public Response<List<StockPrices>> getHistoricalData(
            @PathVariable("code") String stockCode,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        log.info("API: Getting historical data for {} ({} days)", stockCode, days);
        List<StockPrices> prices = stockDataService.fetchHistoricalData(stockCode, days);
        return Response.success(prices);
    }

    /**
     * 获取实时价格
     */
    @GetMapping("/stocks/{code}/price")
    public Response<Double> getRealTimePrice(@PathVariable("code") String stockCode) {
        log.info("API: Getting real-time price for {}", stockCode);
        Double price = stockDataService.fetchRealTimePrice(stockCode);
        return Response.success(price);
    }

    /**
     * 同步单只股票历史数据
     */
    @PostMapping("/stocks/{code}/sync")
    public Response<String> syncStockHistory(
            @PathVariable("code") String stockCode,
            @RequestParam(value = "days", defaultValue = "60") int days) {
        log.info("API: Syncing history for {} ({} days)", stockCode, days);
        stockDataService.syncStockHistory(stockCode, days);
        return Response.success("Stock history sync started for " + stockCode);
    }

    /**
     * 全量同步所有股票数据
     */
    @PostMapping("/sync/all")
    public Response<String> syncAllStocks() {
        log.info("API: Starting full stock data sync");
        stockDataService.syncAllStocks();
        return Response.success("Full stock data sync completed");
    }
}
