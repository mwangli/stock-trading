package online.mwang.stockTrading.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.common.utils.DateUtils;
import online.mwang.stockTrading.modules.decision.DecisionEngine;
import online.mwang.stockTrading.modules.decision.model.TradingSignal;
import online.mwang.stockTrading.modules.prediction.LSTMPredictionService;
import online.mwang.stockTrading.modules.risk.RiskControlService;
import online.mwang.stockTrading.modules.sentiment.SentimentAnalysisService;
import online.mwang.stockTrading.modules.stockselection.StockSelector;
import online.mwang.stockTrading.modules.stockselection.model.SelectResult;
import online.mwang.stockTrading.modules.trading.TradeExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * T+1短线交易定时任务调度器
 * 
 * 交易时序：
 * 09:30 - 执行卖出（T+1平仓，上午时段）
 * 11:00 - 情感分析
 * 11:30 - LSTM预测
 * 12:00 - Dexter分析
 * 12:30 - 综合选股
 * 13:00 - 执行买入（下午买入）
 * 15:30 - 日终结算
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingScheduler {

    private final SentimentAnalysisService sentimentService;
    private final LSTMPredictionService predictionService;
    private final StockSelector stockSelector;
    private final DecisionEngine decisionEngine;
    private final RiskControlService riskControl;
    private final TradeExecutionService tradeExecution;

    /**
     * 09:30 - 执行卖出（T+1平仓）
     * 卖出昨日持仓，优先释放资金
     */
    @Scheduled(cron = "0 30 9 ? * MON-FRI")
    public void executeSell() {
        log.info("【定时任务】09:30 开始执行卖出操作");
        try {
            // 获取所有持仓
            var positions = tradeExecution.getHoldingPositions();
            
            for (var position : positions) {
                // 检查是否触发止损
                var riskCheck = riskControl.checkPositionForSell(position);
                
                if (riskCheck.shouldForceSell()) {
                    log.info("触发止损，强制卖出: {}", position.getStockCode());
                }
                
                // 执行卖出（卖出始终被允许）
                var result = tradeExecution.executeSell(
                    position.getStockCode(), 
                    position.getQuantity()
                );
                
                if (result.isSuccess()) {
                    log.info("卖出成功: {} - 价格: {} - 盈亏: {}", 
                        position.getStockCode(), 
                        result.getFilledPrice(),
                        position.getUnrealizedPnl()
                    );
                } else {
                    log.error("卖出失败: {} - {}", 
                        position.getStockCode(), 
                        result.getErrorMessage()
                    );
                }
            }
        } catch (Exception e) {
            log.error("卖出任务执行异常", e);
        }
    }

    /**
     * 11:00 - 情感分析
     * 上午收盘前进行新闻情感分析
     */
    @Scheduled(cron = "0 0 11 ? * MON-FRI")
    public void analyzeSentiment() {
        log.info("【定时任务】11:00 开始情感分析");
        try {
            long start = System.currentTimeMillis();
            
            // 获取所有股票的情感排名
            var rankings = sentimentService.getStockSentimentRanking();
            
            long end = System.currentTimeMillis();
            log.info("情感分析完成，共分析{}只股票，耗时{}", 
                rankings.size(), 
                DateUtils.timeConvertor(end - start)
            );
        } catch (Exception e) {
            log.error("情感分析任务执行异常", e);
        }
    }

    /**
     * 11:30 - LSTM预测
     * 上午收盘前进行价格预测
     */
    @Scheduled(cron = "0 30 11 ? * MON-FRI")
    public void runLSTMPrediction() {
        log.info("【定时任务】11:30 开始LSTM预测");
        try {
            long start = System.currentTimeMillis();
            
            // 获取所有股票的LSTM预测排名
            var rankings = predictionService.getStockPredictionRanking();
            
            long end = System.currentTimeMillis();
            log.info("LSTM预测完成，共预测{}只股票，耗时{}", 
                rankings.size(), 
                DateUtils.timeConvertor(end - start)
            );
        } catch (Exception e) {
            log.error("LSTM预测任务执行异常", e);
        }
    }

    /**
     * 12:00 - Dexter分析
     * 午休时进行基本面分析
     */
    @Scheduled(cron = "0 0 12 ? * MON-FRI")
    public void runDexterAnalysis() {
        log.info("【定时任务】12:00 开始Dexter分析");
        // TODO: 实现Dexter分析
        log.info("Dexter分析完成");
    }

    /**
     * 12:30 - 综合选股
     * 午后开盘前完成选股
     */
    @Scheduled(cron = "0 30 12 ? * MON-FRI")
    public void selectStock() {
        log.info("【定时任务】12:30 开始综合选股");
        try {
            long start = System.currentTimeMillis();
            
            // 1. 综合选股
            SelectResult result = stockSelector.selectBestStock();
            
            // 2. 生成交易信号
            TradingSignal signal = decisionEngine.generateSignal(result);
            
            // 3. 保存选股结果
            stockSelector.saveSelection(result);
            decisionEngine.saveSignal(signal);
            
            long end = System.currentTimeMillis();
            log.info("选股完成，选中股票: {}，信号: {}，置信度: {}，耗时{}",
                result.getBest() != null ? result.getBest().getStockCode() : "无",
                signal.getSignal(),
                signal.getConfidence(),
                DateUtils.timeConvertor(end - start)
            );
        } catch (Exception e) {
            log.error("选股任务执行异常", e);
        }
    }

    /**
     * 13:00 - 执行买入
     * 下午开盘时执行买入
     */
    @Scheduled(cron = "0 0 13 ? * MON-FRI")
    public void executeBuy() {
        log.info("【定时任务】13:00 开始执行买入操作");
        try {
            // 1. 风控检查
            var riskCheck = riskControl.checkBeforeBuy();
            if (!riskCheck.isPassed()) {
                log.warn("买入被风控拦截: {}", riskCheck.getMessage());
                return;
            }
            
            // 2. 获取今日交易信号
            TradingSignal signal = decisionEngine.getTodaySignal();
            if (signal == null || !signal.isBuy()) {
                log.info("今日无买入信号，跳过买入");
                return;
            }
            
            // 3. 验证信号有效性
            if (!decisionEngine.isSignalValid(signal)) {
                log.warn("交易信号已过期");
                return;
            }
            
            // 4. 执行买入
            var account = tradeExecution.getAccountInfo();
            int quantity = decisionEngine.calculateBuyQuantity(
                account.getAvailableAmount(),
                signal.getStockCode(),
                signal.getSuggestedPrice()
            );
            
            if (quantity > 0) {
                var result = tradeExecution.executeBuy(signal.getStockCode(), quantity);
                if (result.isSuccess()) {
                    log.info("买入成功: {} - 数量: {} - 价格: {}",
                        signal.getStockCode(),
                        result.getFilledQuantity(),
                        result.getFilledPrice()
                    );
                } else {
                    log.error("买入失败: {}", result.getErrorMessage());
                }
            } else {
                log.warn("计算买入数量为0，跳过买入");
            }
            
        } catch (Exception e) {
            log.error("买入任务执行异常", e);
        }
    }

    /**
     * 15:30 - 日终结算
     * 更新盈亏统计
     */
    @Scheduled(cron = "0 30 15 ? * MON-FRI")
    public void dailySettlement() {
        log.info("【定时任务】15:30 开始日终结算");
        try {
            // 计算当日盈亏
            double dailyPnL = riskControl.calculateDailyPnL();
            log.info("当日盈亏: {}%", String.format("%.2f", dailyPnL * 100));
            
            // 计算当月盈亏
            double monthlyPnL = riskControl.calculateMonthlyPnL();
            log.info("当月盈亏: {}%", String.format("%.2f", monthlyPnL * 100));
            
            // 检查风控状态
            var riskLevel = riskControl.getCurrentRiskLevel();
            log.info("当前风控等级: {}", riskLevel.getDescription());
            
        } catch (Exception e) {
            log.error("日终结算任务执行异常", e);
        }
    }

    /**
     * 周六 00:00 - 模型训练
     * 周末离线训练模型
     */
    @Scheduled(cron = "0 0 0 ? * SAT")
    public void trainModels() {
        log.info("【定时任务】周六 00:00 开始模型训练");
        try {
            predictionService.retrainAllModels();
            log.info("模型训练完成");
        } catch (Exception e) {
            log.error("模型训练任务执行异常", e);
        }
    }

    /**
     * 实时监控 - 检查止损
     * 每分钟检查一次持仓是否需要止损
     */
    @Scheduled(cron = "0 * 9-15 ? * MON-FRI")
    public void monitorStopLoss() {
        try {
            var stopLossPositions = riskControl.getStopLossPositions();
            for (var position : stopLossPositions) {
                log.warn("触发止损，执行卖出: {}", position.getStockCode());
                tradeExecution.executeSell(position.getStockCode(), position.getQuantity());
            }
        } catch (Exception e) {
            log.error("止损监控异常", e);
        }
    }
}
