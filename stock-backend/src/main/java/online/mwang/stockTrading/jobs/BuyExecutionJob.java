package online.mwang.stockTrading.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.entities.Position;
import online.mwang.stockTrading.entities.StockRanking;
import online.mwang.stockTrading.repositories.StockRankingRepository;
import online.mwang.stockTrading.results.OrderResult;
import online.mwang.stockTrading.services.TradeExecutionService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 买入执行Job
 * 执行时间: 每天 14:50
 * 任务内容: 根据综合选股结果执行买入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuyExecutionJob implements Job {

    private final TradeExecutionService tradeExecutionService;
    private final StockRankingRepository stockRankingRepository;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("========== 买入执行任务开始 (14:50) ==========");
        try {
            // 1. 检查是否已有持仓
            List<Position> positions = tradeExecutionService.getHoldingPositions();
            if (positions != null && !positions.isEmpty()) {
                log.info("当前已有持仓{}只，暂不执行买入", positions.size());
                return;
            }

            // 2. 获取今日选股结果（综合排名第一的股票）
            LocalDate today = LocalDate.now();
            List<StockRanking> topRankings = stockRankingRepository.findTopCompositeScoreByDate(today, 1);
            
            if (topRankings == null || topRankings.isEmpty()) {
                log.warn("今日无选股结果，无法执行买入");
                return;
            }
            
            String selectedStock = topRankings.get(0).getStockCode();
            log.info("选股结果: stockCode={}, score={}", selectedStock, topRankings.get(0).getCompositeScore());

            // 3. 获取账户信息，计算可买入数量
            var accountInfo = tradeExecutionService.getAccountInfo();
            if (accountInfo == null) {
                log.error("无法获取账户信息");
                return;
            }

            // 4. 获取当前价格
            Double currentPrice = tradeExecutionService.getCurrentPrice(selectedStock);
            if (currentPrice == null || currentPrice <= 0) {
                log.error("无法获取股票价格: {}", selectedStock);
                return;
            }

            // 5. 计算买入数量（100股整数倍，使用可用资金的80%）
            double availableCash = accountInfo.getAvailableCash().doubleValue();
            double usableAmount = availableCash * 0.8; // 使用80%可用资金
            int quantity = (int) (usableAmount / currentPrice / 100) * 100; // 100股整数倍

            if (quantity < 100) {
                log.warn("可用资金不足，最小买入100股，当前可买: {}", quantity);
                return;
            }

            // 6. 执行买入
            log.info("执行买入: stockCode={}, quantity={}, price={}", selectedStock, quantity, currentPrice);
            OrderResult result = tradeExecutionService.executeBuy(selectedStock, quantity);

            if (result.isSuccess()) {
                log.info("买入成功: stockCode={}, quantity={}, price={}, orderId={}",
                        selectedStock, quantity, currentPrice, result.getOrderId());
            } else {
                log.error("买入失败: stockCode={}, error={}", selectedStock, result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("买入执行任务执行失败: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
        log.info("========== 买入执行任务结束 ==========");
    }
}
