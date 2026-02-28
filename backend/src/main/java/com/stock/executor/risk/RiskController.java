package com.stock.executor.risk;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RiskController {

    private static final double MAX_DAILY_LOSS = 3.0;
    private static final double MAX_MONTHLY_LOSS = 10.0;
    private static final double MAX_SINGLE_POSITION = 30.0;
    private static final double MAX_TOTAL_POSITION = 80.0;

    public RiskCheckResult checkBeforeBuy(String stockCode, BigDecimal amount) {
        List<String> violations = new ArrayList<>();
        
        // 获取当前账户状态
        AccountStatus account = getAccountStatus();
        
        // 规则1: 日亏损检查
        if (account.getDailyLossPercent() > MAX_DAILY_LOSS) {
            violations.add(String.format("当日亏损 %.2f%% 已超过 %.2f%%, 禁止买入", 
                    account.getDailyLossPercent(), MAX_DAILY_LOSS));
        }
        
        // 规则2: 月亏损检查
        if (account.getMonthlyLossPercent() > MAX_MONTHLY_LOSS) {
            violations.add(String.format("当月亏损 %.2f%% 已超过 %.2f%%, 禁止买入", 
                    account.getMonthlyLossPercent(), MAX_MONTHLY_LOSS));
        }
        
        // 规则3: 单股仓位检查
        double singlePosition = calculatePositionPercent(stockCode, account);
        if (singlePosition > MAX_SINGLE_POSITION) {
            violations.add(String.format("单只股票仓位 %.2f%% 已超过 %.2f%%", 
                    singlePosition, MAX_SINGLE_POSITION));
        }
        
        // 规则4: 总仓位检查
        if (account.getTotalPositionPercent() > MAX_TOTAL_POSITION) {
            violations.add(String.format("总仓位 %.2f%% 已超过 %.2f%%", 
                    account.getTotalPositionPercent(), MAX_TOTAL_POSITION));
        }
        
        // 规则5: 买入金额检查
        if (amount.compareTo(account.getAvailableCash()) > 0) {
            violations.add("买入金额超过可用资金");
        }
        
        boolean passed = violations.isEmpty();
        
        log.info("风控检查结果: {}, 违规数: {}", passed ? "通过" : "拒绝", violations.size());
        
        return RiskCheckResult.builder()
                .passed(passed)
                .violations(violations)
                .accountStatus(account)
                .build();
    }

    public RiskCheckResult checkBeforeSell(String stockCode, BigDecimal quantity) {
        List<String> violations = new ArrayList<>();
        
        AccountStatus account = getAccountStatus();
        
        // 获取持仓
        Position position = getPosition(stockCode);
        if (position == null) {
            violations.add("无该股票持仓");
        }
        
        boolean passed = violations.isEmpty();
        
        return RiskCheckResult.builder()
                .passed(passed)
                .violations(violations)
                .accountStatus(account)
                .build();
    }

    private AccountStatus getAccountStatus() {
        // TODO: 从数据库/缓存获取真实账户状态
        AccountStatus status = new AccountStatus();
        status.setTotalAssets(new BigDecimal("1000000"));
        status.setAvailableCash(new BigDecimal("500000"));
        status.setDailyProfitLossPercent(1.5);
        status.setMonthlyProfitLossPercent(-5.0);
        status.setTotalPositionPercent(50.0);
        return status;
    }

    private Position getPosition(String stockCode) {
        // TODO: 从数据库获取持仓
        return null;
    }

    private double calculatePositionPercent(String stockCode, AccountStatus account) {
        // TODO: 计算单只股票仓位
        return 20.0;
    }

    @Data
    public static class AccountStatus {
        private BigDecimal totalAssets;
        private BigDecimal availableCash;
        private double dailyProfitLossPercent;
        private double monthlyProfitLossPercent;
        private double totalPositionPercent;

        public double getDailyLossPercent() {
            return Math.max(0, -dailyProfitLossPercent);
        }

        public double getMonthlyLossPercent() {
            return Math.max(0, -monthlyProfitLossPercent);
        }
    }

    @Data
    public static class Position {
        private String stockCode;
        private BigDecimal quantity;
        private BigDecimal avgCost;
        private BigDecimal currentPrice;
    }

    @Data
    @lombok.Builder
    public static class RiskCheckResult {
        private boolean passed;
        private List<String> violations;
        private AccountStatus accountStatus;
    }
}
