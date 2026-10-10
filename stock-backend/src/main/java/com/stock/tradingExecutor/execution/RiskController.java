// AI_GENERATE_START --
package com.stock.tradingExecutor.execution;

import com.stock.dataCollector.domain.entity.OpenTradabilityStatus;
import com.stock.dataCollector.domain.entity.StockDailyTradabilityFact;
import com.stock.dataCollector.persistence.StockDailyTradabilityFactRepository;
import com.stock.tradingExecutor.config.RiskConfig;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerFillSnapshot;
import com.stock.tradingExecutor.domain.vo.RiskCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实委托前风控检查器。
 * 使用券商账户、持仓、当日成交和已导入可成交性事实进行失败即停止的校验。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskController {

    private final BrokerAdapter brokerAdapter;
    private final RiskConfig config;
    private final TradingTimeChecker tradingTimeChecker;
    private final StockDailyTradabilityFactRepository tradabilityFactRepository;

    /**
     * 执行买入前风控检查。
     *
     * @param stockCode 股票代码
     * @param amount 计划买入金额
     * @return 风控结果
     */
    public RiskCheckResult checkBeforeBuy(String stockCode, BigDecimal amount) {
        List<String> violations = new ArrayList<>();
        AccountStatus account = brokerAdapter.getAccountInfo();

        if (stockCode == null || stockCode.isBlank()) {
            violations.add("股票代码不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            violations.add("买入金额必须大于零");
        }
        if (account == null) {
            violations.add("未获得真实账户资金信息");
        } else if (amount != null) {
            checkAccountBuyLimits(stockCode, amount, account, violations);
        }
        checkTradability(stockCode, true, violations);

        if (!tradingTimeChecker.isTradingTime()) {
            violations.add("非交易时间，禁止买入");
        }
        if (tradingTimeChecker.isPastBuyDeadLine()) {
            violations.add("已过买入截止时间，禁止买入");
        }

        boolean passed = violations.isEmpty();
        log.info("买入风控检查: {} 结果={}, 违规数={}", stockCode, passed ? "通过" : "拒绝", violations.size());
        return passed ? RiskCheckResult.pass(account) : RiskCheckResult.reject(violations, account);
    }

    /**
     * 执行卖出前风控检查。
     *
     * @param stockCode 股票代码
     * @param quantity 卖出数量
     * @return 风控结果
     */
    public RiskCheckResult checkBeforeSell(String stockCode, BigDecimal quantity) {
        List<String> violations = new ArrayList<>();
        AccountStatus account = brokerAdapter.getAccountInfo();
        Position position = getPosition(stockCode);

        if (account == null) {
            violations.add("未获得真实账户资金信息");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0
                || quantity.stripTrailingZeros().scale() > 0) {
            violations.add("卖出数量必须是正整数");
        }
        if (position == null) {
            violations.add("无该股票持仓");
        } else if (quantity != null) {
            int availableQuantity = position.getAvailableQuantity() == null ? 0 : position.getAvailableQuantity();
            if (quantity.compareTo(BigDecimal.valueOf(availableQuantity)) > 0) {
                violations.add(String.format("卖出数量 %s 超过可卖数量 %d", quantity, availableQuantity));
            }
        }

        if (hasTodayBuyFill(stockCode)) {
            violations.add("当日存在真实买入成交，T+1 才能卖出");
        }
        checkTradability(stockCode, false, violations);

        if (!tradingTimeChecker.isTradingTime()) {
            violations.add("非交易时间，禁止卖出");
        }

        boolean passed = violations.isEmpty();
        log.info("卖出风控检查: {} 结果={}, 违规数={}", stockCode, passed ? "通过" : "拒绝", violations.size());
        return passed ? RiskCheckResult.pass(account) : RiskCheckResult.reject(violations, account);
    }

    /**
     * 判断单只持仓是否达到止损阈值。
     *
     * @param stockCode 股票代码
     * @return 达到止损阈值时返回 true
     */
    public boolean shouldStopLoss(String stockCode) {
        Position position = getPosition(stockCode);
        if (position == null || position.getProfitLossPercent() == null) {
            return false;
        }
        double lossPercent = position.getProfitLossPercent().negate().doubleValue();
        if (lossPercent > config.getSingleStockStopLoss()) {
            log.warn("触发个股止损: {} 亏损={}%", stockCode, lossPercent);
            return true;
        }
        return false;
    }

    /**
     * 判断账户是否达到总止损阈值。
     *
     * @return 达到总止损阈值时返回 true
     */
    public boolean shouldLiquidate() {
        AccountStatus account = brokerAdapter.getAccountInfo();
        if (account == null) {
            return false;
        }
        double totalLossPercent = account.getDailyLossPercent();
        if (totalLossPercent > config.getTotalStopLoss()) {
            log.warn("触发总止损清仓: 总亏损={}%", totalLossPercent);
            return true;
        }
        return false;
    }

    /**
     * 计算指定行业持仓占总资产比例。
     *
     * @param industry 行业名称
     * @return 行业持仓比例，单位为百分比
     */
    public double calculateIndustryConcentration(String industry) {
        List<Position> positions = brokerAdapter.getPositions();
        if (positions.isEmpty()) {
            return 0D;
        }
        AccountStatus account = brokerAdapter.getAccountInfo();
        if (account == null || account.getTotalAssets() == null
                || account.getTotalAssets().compareTo(BigDecimal.ZERO) <= 0) {
            return 0D;
        }
        BigDecimal industryValue = positions.stream()
                .filter(position -> industry.equals(position.getIndustry()))
                .map(Position::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return industryValue.divide(account.getTotalAssets(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private void checkAccountBuyLimits(String stockCode, BigDecimal amount, AccountStatus account,
                                       List<String> violations) {
        if (account.getDailyLossPercent() > config.getMaxDailyLoss()) {
            violations.add(String.format("当日亏损 %.2f%% 已超过 %.2f%%, 禁止买入",
                    account.getDailyLossPercent(), config.getMaxDailyLoss()));
        }
        if (account.getMonthlyLossPercent() > config.getMaxMonthlyLoss()) {
            violations.add(String.format("当月亏损 %.2f%% 已超过 %.2f%%, 禁止买入",
                    account.getMonthlyLossPercent(), config.getMaxMonthlyLoss()));
        }
        if (account.getTotalPositionPercent() > config.getMaxTotalPosition()) {
            violations.add(String.format("总仓位 %.2f%% 已超过 %.2f%%",
                    account.getTotalPositionPercent(), config.getMaxTotalPosition()));
        }
        double singlePosition = calculateSinglePositionPercent(stockCode, amount, account);
        if (singlePosition > config.getMaxSinglePosition()) {
            violations.add(String.format("买入后单股仓位 %.2f%% 将超过 %.2f%%",
                    singlePosition, config.getMaxSinglePosition()));
        }
        if (account.getAvailableCash() == null || amount.compareTo(account.getAvailableCash()) > 0) {
            violations.add("买入金额超过真实账户可用资金");
        }
        if (amount.compareTo(BigDecimal.valueOf(config.getMinOrderAmount())) < 0) {
            violations.add(String.format("买入金额 %.0f 低于最小金额 %.0f",
                    amount, config.getMinOrderAmount()));
        }
    }

    private void checkTradability(String stockCode, boolean buy, List<String> violations) {
        if (stockCode == null || stockCode.isBlank()) {
            return;
        }
        StockDailyTradabilityFact fact = tradabilityFactRepository
                .findFirstByStockCodeAndTradeDateOrderByCapturedAtDesc(stockCode, LocalDate.now())
                .orElse(null);
        if (fact == null) {
            violations.add("缺少当日可成交性事实，停止真实交易");
            return;
        }
        OpenTradabilityStatus status = buy ? fact.getOpenBuyStatus() : fact.getOpenSellStatus();
        if (status != OpenTradabilityStatus.ALLOWED) {
            String reason = buy ? fact.getBuyReason() : fact.getSellReason();
            violations.add("当前方向不可成交: " + status + (reason == null ? "" : " - " + reason));
        }
    }

    private boolean hasTodayBuyFill(String stockCode) {
        return brokerAdapter.getTodayFillSnapshots().stream()
                .filter(fill -> stockCode != null && stockCode.equals(fill.getStockCode()))
                .map(BrokerFillSnapshot::getDirection)
                .anyMatch(direction -> "BUY".equalsIgnoreCase(direction));
    }

    private Position getPosition(String stockCode) {
        return brokerAdapter.getPositions().stream()
                .filter(position -> stockCode != null && stockCode.equals(position.getStockCode()))
                .findFirst()
                .orElse(null);
    }

    private double calculateSinglePositionPercent(String stockCode, BigDecimal amount, AccountStatus account) {
        BigDecimal currentPositionValue = BigDecimal.ZERO;
        Position position = getPosition(stockCode);
        if (position != null && position.getMarketValue() != null) {
            currentPositionValue = position.getMarketValue();
        }
        BigDecimal totalAssets = account.getTotalAssets();
        if (totalAssets == null || totalAssets.compareTo(BigDecimal.ZERO) <= 0) {
            return 100D;
        }
        return currentPositionValue.add(amount)
                .divide(totalAssets, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
// AI_GENERATE_END --
