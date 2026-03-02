package com.stock.tradingExecutor.risk;

import com.stock.tradingExecutor.broker.BrokerAdapter;
import com.stock.tradingExecutor.config.RiskConfig;
import com.stock.tradingExecutor.entity.AccountStatus;
import com.stock.tradingExecutor.entity.Position;
import com.stock.tradingExecutor.entity.RiskCheckResult;
import com.stock.tradingExecutor.time.TradingTimeChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控检查器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskController {
    
    private final BrokerAdapter brokerAdapter;
    private final RiskConfig config;
    private final TradingTimeChecker tradingTimeChecker;
    
    /**
     * 买入前风控检查
     * 
     * @param stockCode 股票代码
     * @param amount 买入金额
     * @return 风控检查结果
     */
    public RiskCheckResult checkBeforeBuy(String stockCode, BigDecimal amount) {
        List<String> violations = new ArrayList<>();
        
        // 获取当前账户状态
        AccountStatus account = brokerAdapter.getAccountInfo();
        
        // 规则1: 日亏损检查
        if (account.getDailyLossPercent() > config.getMaxDailyLoss()) {
            violations.add(String.format("当日亏损 %.2f%% 已超过 %.2f%%, 禁止买入",
                    account.getDailyLossPercent(), config.getMaxDailyLoss()));
        }
        
        // 规则2: 月亏损检查
        if (account.getMonthlyLossPercent() > config.getMaxMonthlyLoss()) {
            violations.add(String.format("当月亏损 %.2f%% 已超过 %.2f%%, 禁止买入",
                    account.getMonthlyLossPercent(), config.getMaxMonthlyLoss()));
        }
        
        // 规则3: 总仓位检查
        double totalPosition = account.getTotalPositionPercent();
        if (totalPosition > config.getMaxTotalPosition()) {
            violations.add(String.format("总仓位 %.2f%% 已超过 %.2f%%",
                    totalPosition, config.getMaxTotalPosition()));
        }
        
        // 规则4: 单股仓位检查
        double singlePosition = calculateSinglePositionPercent(stockCode, amount, account);
        if (singlePosition > config.getMaxSinglePosition()) {
            violations.add(String.format("买入后单股仓位 %.2f%% 将超过 %.2f%%",
                    singlePosition, config.getMaxSinglePosition()));
        }
        
        // 规则5: 买入金额检查
        if (amount.compareTo(account.getAvailableCash()) > 0) {
            violations.add(String.format("买入金额 %s 超过可用资金 %s",
                    amount, account.getAvailableCash()));
        }
        
        // 规则6: 最小金额检查
        if (amount.compareTo(BigDecimal.valueOf(config.getMinOrderAmount())) < 0) {
            violations.add(String.format("买入金额 %.0f 低于最小金额 %.0f",
                    amount, config.getMinOrderAmount()));
        }
        
        // 规则7: 涨停检查
        if (isLimitUp(stockCode)) {
            violations.add("股票涨停，禁止买入");
        }
        
        // 规则8: 交易时间检查
        if (!tradingTimeChecker.isTradingTime()) {
            violations.add("非交易时间，禁止买入");
        }
        
        // 规则9: 买入截止时间检查
        if (tradingTimeChecker.isPastBuyDeadLine()) {
            violations.add("已过买入截止时间，禁止买入");
        }
        
        boolean passed = violations.isEmpty();
        
        log.info("买入风控检查: {} 结果={}, 违规数={}", stockCode, passed ? "通过" : "拒绝", violations.size());
        
        return passed ? RiskCheckResult.pass(account) : RiskCheckResult.reject(violations, account);
    }
    
    /**
     * 卖出前风控检查
     * 
     * @param stockCode 股票代码
     * @param quantity 卖出数量
     * @return 风控检查结果
     */
    public RiskCheckResult checkBeforeSell(String stockCode, BigDecimal quantity) {
        List<String> violations = new ArrayList<>();
        
        AccountStatus account = brokerAdapter.getAccountInfo();
        
        // 获取持仓
        Position position = getPosition(stockCode);
        
        // 规则1: 持仓检查
        if (position == null) {
            violations.add("无该股票持仓");
        } else {
            // 规则2: 持仓数量检查
            if (quantity.compareTo(BigDecimal.valueOf(position.getQuantity())) > 0) {
                violations.add(String.format("卖出数量 %s 超过持仓数量 %d",
                        quantity, position.getQuantity()));
            }
            
            // 规则3: T+1检查
            if (position.getBuyDate() != null && 
                    position.getBuyDate().isEqual(LocalDate.now())) {
                violations.add("当日买入股票，T+1才能卖出");
            }
        }
        
        // 规则4: 跌停检查
        if (isLimitDown(stockCode)) {
            violations.add("股票跌停，禁止卖出");
        }
        
        // 规则5: 交易时间检查
        if (!tradingTimeChecker.isTradingTime()) {
            violations.add("非交易时间，禁止卖出");
        }
        
        boolean passed = violations.isEmpty();
        
        log.info("卖出风控检查: {} 结果={}, 违规数={}", stockCode, passed ? "通过" : "拒绝", violations.size());
        
        return passed ? RiskCheckResult.pass(account) : RiskCheckResult.reject(violations, account);
    }
    
    /**
     * 检查是否触发止损
     * 
     * @param stockCode 股票代码
     * @return 是否触发止损
     */
    public boolean shouldStopLoss(String stockCode) {
        Position position = getPosition(stockCode);
        if (position == null) {
            return false;
        }
        
        BigDecimal profitLossPercent = position.getProfitLossPercent();
        if (profitLossPercent == null) {
            return false;
        }
        
        // 个股止损: 亏损超过阈值
        double lossPercent = profitLossPercent.negate().doubleValue();
        if (lossPercent > config.getSingleStockStopLoss()) {
            log.warn("触发个股止损: {} 亏损={}%", stockCode, lossPercent);
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否触发清仓止损
     * 
     * @return 是否触发清仓
     */
    public boolean shouldLiquidate() {
        AccountStatus account = brokerAdapter.getAccountInfo();
        
        // 总止损: 总亏损超过阈值
        double totalLossPercent = account.getDailyLossPercent();
        if (totalLossPercent > config.getTotalStopLoss()) {
            log.warn("触发总止损清仓: 总亏损={}%", totalLossPercent);
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取持仓
     */
    private Position getPosition(String stockCode) {
        return brokerAdapter.getPositions().stream()
                .filter(p -> stockCode.equals(p.getStockCode()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 计算买入后单股仓位比例
     */
    private double calculateSinglePositionPercent(String stockCode, BigDecimal amount, AccountStatus account) {
        // 获取当前持仓市值
        BigDecimal currentPositionValue = BigDecimal.ZERO;
        Position position = getPosition(stockCode);
        if (position != null && position.getMarketValue() != null) {
            currentPositionValue = position.getMarketValue();
        }
        
        // 买入后总市值
        BigDecimal newValue = currentPositionValue.add(amount);
        
        // 计算比例
        if (account.getTotalAssets().compareTo(BigDecimal.ZERO) > 0) {
            return newValue.divide(account.getTotalAssets(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
        
        return 0.0;
    }
    
    /**
     * 检查是否涨停
     */
    private boolean isLimitUp(String stockCode) {
        // TODO: 实现涨停检查逻辑
        return false;
    }
    
    /**
     * 检查是否跌停
     */
    private boolean isLimitDown(String stockCode) {
        // TODO: 实现跌停检查逻辑
        return false;
    }
    
    /**
     * 检查行业集中度
     * 
     * @param industry 行业
     * @return 集中度百分比
     */
    public double calculateIndustryConcentration(String industry) {
        List<Position> positions = brokerAdapter.getPositions();
        if (positions.isEmpty()) {
            return 0.0;
        }
        
        AccountStatus account = brokerAdapter.getAccountInfo();
        BigDecimal totalAssets = account.getTotalAssets();
        if (totalAssets.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        
        // 计算指定行业的持仓市值
        BigDecimal industryValue = positions.stream()
                .filter(p -> industry.equals(p.getIndustry()))
                .map(Position::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return industryValue.divide(totalAssets, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}