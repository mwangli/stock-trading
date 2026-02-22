package online.mwang.stockTrading.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.dto.Response;
import online.mwang.stockTrading.entities.AccountInfo;
import online.mwang.stockTrading.entities.OrderInfo;
import online.mwang.stockTrading.entities.Position;
import online.mwang.stockTrading.results.OrderResult;
import online.mwang.stockTrading.services.TradeExecutionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交易执行控制器
 * 提供交易执行的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
public class TradeExecutionController {

    private final TradeExecutionService tradeExecutionService;

    /**
     * 执行买入
     */
    @PostMapping("/buy")
    public Response<OrderResult> executeBuy(
            @RequestParam String stockCode,
            @RequestParam int quantity) {
        log.info("API: Execute buy - stockCode: {}, quantity: {}", stockCode, quantity);
        OrderResult result = tradeExecutionService.executeBuy(stockCode, quantity);
        return Response.success(result);
    }

    /**
     * 执行卖出
     */
    @PostMapping("/sell")
    public Response<OrderResult> executeSell(
            @RequestParam String stockCode,
            @RequestParam int quantity) {
        log.info("API: Execute sell - stockCode: {}, quantity: {}", stockCode, quantity);
        OrderResult result = tradeExecutionService.executeSell(stockCode, quantity);
        return Response.success(result);
    }

    /**
     * 获取当前持仓列表
     */
    @GetMapping("/positions")
    public Response<List<Position>> getHoldingPositions() {
        log.info("API: Get holding positions");
        List<Position> positions = tradeExecutionService.getHoldingPositions();
        return Response.success(positions);
    }

    /**
     * 获取账户资金信息
     */
    @GetMapping("/account")
    public Response<AccountInfo> getAccountInfo() {
        log.info("API: Get account info");
        AccountInfo accountInfo = tradeExecutionService.getAccountInfo();
        return Response.success(accountInfo);
    }

    /**
     * 获取今日订单
     */
    @GetMapping("/orders/today")
    public Response<List<OrderInfo>> getTodayOrders() {
        log.info("API: Get today orders");
        List<OrderInfo> orders = tradeExecutionService.getTodayOrder();
        return Response.success(orders);
    }

    /**
     * 获取历史订单
     */
    @GetMapping("/orders/history")
    public Response<List<OrderInfo>> getHistoryOrders() {
        log.info("API: Get history orders");
        List<OrderInfo> orders = tradeExecutionService.getHistoryOrder();
        return Response.success(orders);
    }

    /**
     * 撤销所有无效订单
     */
    @PostMapping("/orders/cancel-all")
    public Response<Integer> cancelAllOrders() {
        log.info("API: Cancel all invalid orders");
        Integer count = tradeExecutionService.cancelAllOrder();
        return Response.success(count);
    }

    /**
     * 止损检查（用于定时任务触发）
     */
    @PostMapping("/stop-loss/check")
    public Response<String> checkStopLoss() {
        log.info("API: Check stop loss");
        tradeExecutionService.retrySellIfNeeded();
        return Response.success("Stop loss check completed");
    }

    /**
     * 获取当前价格
     */
    @GetMapping("/price/{stockCode}")
    public Response<Double> getCurrentPrice(@PathVariable String stockCode) {
        log.info("API: Get current price for {}", stockCode);
        Double price = tradeExecutionService.getCurrentPrice(stockCode);
        return Response.success(price);
    }
}
