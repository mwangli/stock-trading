package com.stock.tradingExecutor.fee;

import com.stock.tradingExecutor.config.FeeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 手续费计算器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeeCalculator {
    
    private final FeeConfig config;
    
    /**
     * 计算手续费
     * 规则: 万分之五，最低5元
     * 
     * @param amount 交易金额
     * @return 手续费
     */
    public BigDecimal calculate(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // 计算手续费 = 金额 * 费率
        BigDecimal fee = amount.multiply(BigDecimal.valueOf(config.getFeeRate()));
        
        // 最低手续费
        BigDecimal minFee = BigDecimal.valueOf(config.getMinFee());
        
        // 取较大值
        return fee.max(minFee).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算买入总成本
     * 
     * @param price 买入价格
     * @param quantity 买入数量
     * @return 总成本 (含手续费)
     */
    public BigDecimal calculateBuyCost(BigDecimal price, int quantity) {
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = calculate(amount);
        return amount.add(fee);
    }
    
    /**
     * 计算卖出净收入
     * 
     * @param price 卖出价格
     * @param quantity 卖出数量
     * @return 净收入 (扣除手续费)
     */
    public BigDecimal calculateSellIncome(BigDecimal price, int quantity) {
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = calculate(amount);
        return amount.subtract(fee);
    }
    
    /**
     * 计算交易总成本 (买入+卖出双边手续费)
     * 
     * @param price 价格
     * @param quantity 数量
     * @return 总手续费
     */
    public BigDecimal calculateTotalFee(BigDecimal price, int quantity) {
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal singleFee = calculate(amount);
        return singleFee.multiply(BigDecimal.valueOf(2));
    }
    
    /**
     * 计算盈亏平衡点涨幅
     * 需要覆盖双边手续费
     * 
     * @return 需要的涨幅比例
     */
    public double calculateBreakEvenPercent() {
        // 双边手续费率
        double totalFeeRate = config.getFeeRate() * 2;
        // 考虑最低手续费的影响，假设平均交易金额为10万
        double avgAmount = 100000;
        double minFeeRate = config.getMinFee() / avgAmount;
        
        // 取较大值
        return Math.max(totalFeeRate, minFeeRate) * 100;
    }
}