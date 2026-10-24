// AI_GENERATE_START---
package com.stock.decision.service;

import com.stock.decision.config.BacktestProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 回测资金场景模拟器。
 *
 * 负责按资金规模、滑点和费用更新资本曲线并计算最大回撤。
 *
 * @author mwangli
 * @since 2026-10-23
 */
final class BacktestCapitalScenario {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    private final BigDecimal initialCapital;
    private final boolean includeCosts;
    private BigDecimal capital;
    private BigDecimal peakCapital;
    private BigDecimal maxDrawdown = BigDecimal.ZERO;

    private BacktestCapitalScenario(BigDecimal initialCapital, boolean includeCosts) {
        this.initialCapital = initialCapital;
        this.includeCosts = includeCosts;
        this.capital = initialCapital;
        this.peakCapital = initialCapital;
    }

    static BacktestCapitalScenario gross(BigDecimal initialCapital) {
        return new BacktestCapitalScenario(initialCapital, false);
    }

    static BacktestCapitalScenario net(BigDecimal initialCapital) {
        return new BacktestCapitalScenario(initialCapital, true);
    }

    Trade execute(BigDecimal rawEntryPrice, BigDecimal rawExitPrice,
                  BacktestProperties properties, boolean applyCosts) {
        BigDecimal slippageRate = properties.getSlippageBps()
                .divide(TEN_THOUSAND, 10, RoundingMode.HALF_UP);
        BigDecimal entryPrice = applyCosts
                ? rawEntryPrice.multiply(BigDecimal.ONE.add(slippageRate)) : rawEntryPrice;
        BigDecimal exitPrice = applyCosts
                ? rawExitPrice.multiply(BigDecimal.ONE.subtract(slippageRate)) : rawExitPrice;
        int quantity = affordableQuantity(entryPrice, properties, applyCosts);
        if (quantity < 100) {
            return new Trade(0, entryPrice, exitPrice, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal shares = BigDecimal.valueOf(quantity);
        BigDecimal buyAmount = entryPrice.multiply(shares);
        BigDecimal sellAmount = exitPrice.multiply(shares);
        BigDecimal buyFee = applyCosts ? buyFee(buyAmount, properties) : BigDecimal.ZERO;
        BigDecimal sellFee = applyCosts ? sellFee(sellAmount, properties) : BigDecimal.ZERO;
        BigDecimal netProfit = sellAmount.subtract(sellFee).subtract(buyAmount).subtract(buyFee);
        updateCapital(netProfit);
        return new Trade(quantity, entryPrice, exitPrice,
                buyFee.add(sellFee).setScale(2, RoundingMode.HALF_UP),
                netProfit.setScale(2, RoundingMode.HALF_UP));
    }

    Trade executeWithQuantity(BigDecimal entryPrice, BigDecimal exitPrice, int quantity) {
        if (quantity < 100) {
            return new Trade(0, entryPrice, exitPrice, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal profit = exitPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(quantity));
        updateCapital(profit);
        return new Trade(quantity, entryPrice, exitPrice, BigDecimal.ZERO,
                profit.setScale(2, RoundingMode.HALF_UP));
    }

    double returnRate() {
        return capital.divide(initialCapital, 10, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).doubleValue();
    }

    double maxDrawdown() {
        return includeCosts ? maxDrawdown.doubleValue() : 0D;
    }

    private void updateCapital(BigDecimal profit) {
        capital = capital.add(profit);
        if (capital.compareTo(peakCapital) > 0) {
            peakCapital = capital;
        }
        BigDecimal drawdown = peakCapital.signum() == 0 ? BigDecimal.ZERO
                : peakCapital.subtract(capital).divide(peakCapital, 10, RoundingMode.HALF_UP);
        if (drawdown.compareTo(maxDrawdown) > 0) {
            maxDrawdown = drawdown;
        }
    }

    private int affordableQuantity(BigDecimal entryPrice, BacktestProperties properties,
                                   boolean applyCosts) {
        BigDecimal reservedFee = applyCosts ? properties.getMinimumCommission() : BigDecimal.ZERO;
        BigDecimal available = capital.subtract(reservedFee);
        if (available.signum() <= 0) {
            return 0;
        }
        int quantity = available.divide(entryPrice.multiply(ONE_HUNDRED), 0, RoundingMode.DOWN)
                .intValue() * 100;
        while (quantity >= 100) {
            BigDecimal amount = entryPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal fee = applyCosts ? buyFee(amount, properties) : BigDecimal.ZERO;
            if (amount.add(fee).compareTo(capital) <= 0) {
                return quantity;
            }
            quantity -= 100;
        }
        return 0;
    }

    private BigDecimal buyFee(BigDecimal amount, BacktestProperties properties) {
        return commission(amount, properties).add(amount.multiply(properties.getTransferFeeRate()));
    }

    private BigDecimal sellFee(BigDecimal amount, BacktestProperties properties) {
        return commission(amount, properties)
                .add(amount.multiply(properties.getStampDutyRate()))
                .add(amount.multiply(properties.getTransferFeeRate()));
    }

    private BigDecimal commission(BigDecimal amount, BacktestProperties properties) {
        return amount.multiply(properties.getCommissionRate()).max(properties.getMinimumCommission());
    }

    record Trade(int quantity, BigDecimal entryPrice, BigDecimal exitPrice,
                 BigDecimal totalFee, BigDecimal netProfit) {
    }
}
// AI_GENERATE_END---
