package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.domain.vo.MinuteBar;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 移动止损计算器
 * 动态跟踪最高价，计算止损线
 */
@Slf4j
@Component
public class TrailingStopCalculator {

    private static final String REDIS_HIGH_PRICE_KEY = "stock:%s:highPrice:";
    private static final String REDIS_STOP_LOSS_KEY = "stock:%s:stopLoss:";

    private final RedisTemplate<String, Object> redisTemplate;

    public TrailingStopCalculator(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 更新止损线
     * 当价格创新高时，更新止损线
     *
     * @param stockCode 股票代码
     * @param currentPrice 当前价格
     * @param dailyAmplitude 日振幅（用于动态调整回撤容忍度）
     * @param config 策略配置
     * @return 更新后的止损线
     */
    public double updateStopLoss(String stockCode, double currentPrice, double dailyAmplitude, StrategyConfig config) {
        String today = LocalDate.now().toString();
        String highPriceKey = String.format(REDIS_HIGH_PRICE_KEY, stockCode) + today;
        String stopLossKey = String.format(REDIS_STOP_LOSS_KEY, stockCode) + today;

        // 获取当前最高价
        Object cached = redisTemplate.opsForValue().get(highPriceKey);
        double currentHighPrice = cached != null ? ((Number) cached).doubleValue() : 0;

        // 计算动态回撤容忍度
        double tolerance = calculateDynamicTolerance(dailyAmplitude, config);

        // 如果当前价格创新高，更新止损线
        if (currentPrice > currentHighPrice) {
            currentHighPrice = currentPrice;
            redisTemplate.opsForValue().set(highPriceKey, currentHighPrice);

            double stopLoss = currentHighPrice * (1 - tolerance);
            redisTemplate.opsForValue().set(stopLossKey, stopLoss);

            log.debug("股票 {} 创新高 {}, 更新止损线为 {} (容忍度 {}%)",
                    stockCode, currentHighPrice, stopLoss, tolerance * 100);
        }

        return currentHighPrice * (1 - tolerance);
    }

    /**
     * 检查是否触发移动止损
     *
     * @param stockCode 股票代码
     * @param currentPrice 当前价格
     * @param config 策略配置
     * @return 是否触发止损
     */
    public boolean checkTrigger(String stockCode, double currentPrice, StrategyConfig config) {
        String today = LocalDate.now().toString();
        String stopLossKey = String.format(REDIS_STOP_LOSS_KEY, stockCode) + today;

        Object cached = redisTemplate.opsForValue().get(stopLossKey);
        if (cached == null) {
            return false;
        }

        double stopLoss = ((Number) cached).doubleValue();
        boolean triggered = currentPrice <= stopLoss;

        if (triggered) {
            log.info("股票 {} 触发移动止损: 当前价 {}, 止损线 {}", stockCode, currentPrice, stopLoss);
        }

        return triggered;
    }

    /**
     * 获取当前止损线
     */
    public double getStopLossPrice(String stockCode) {
        String today = LocalDate.now().toString();
        String stopLossKey = String.format(REDIS_STOP_LOSS_KEY, stockCode) + today;

        Object cached = redisTemplate.opsForValue().get(stopLossKey);
        return cached != null ? ((Number) cached).doubleValue() : 0;
    }

    /**
     * 获取当日最高价
     */
    public double getHighPrice(String stockCode) {
        String today = LocalDate.now().toString();
        String highPriceKey = String.format(REDIS_HIGH_PRICE_KEY, stockCode) + today;

        Object cached = redisTemplate.opsForValue().get(highPriceKey);
        return cached != null ? ((Number) cached).doubleValue() : 0;
    }

    /**
     * 重置止损状态（新交易日开始时调用）
     */
    public void reset(String stockCode, double openPrice, StrategyConfig config) {
        String today = LocalDate.now().toString();
        String highPriceKey = String.format(REDIS_HIGH_PRICE_KEY, stockCode) + today;
        String stopLossKey = String.format(REDIS_STOP_LOSS_KEY, stockCode) + today;

        // 开盘价作为初始最高价
        redisTemplate.opsForValue().set(highPriceKey, openPrice);

        // 计算初始止损线
        double stopLoss = openPrice * (1 - config.getTrailingStopTolerance());
        redisTemplate.opsForValue().set(stopLossKey, stopLoss);

        log.debug("重置股票 {} 止损状态: 开盘价 {}, 止损线 {}", stockCode, openPrice, stopLoss);
    }

    /**
     * 计算动态回撤容忍度
     * 根据日振幅调整：高波动股票使用更大的容忍度
     */
    private double calculateDynamicTolerance(double dailyAmplitude, StrategyConfig config) {
        double baseTolerance = config.getTrailingStopTolerance();

        // 日振幅 > 5%，高波动股票，容忍度增加到 3%
        if (dailyAmplitude > 0.05) {
            return Math.max(baseTolerance, 0.03);
        }
        // 日振幅 < 3%，低波动股票，容忍度降低到 1%
        else if (dailyAmplitude < 0.03) {
            return Math.min(baseTolerance, 0.01);
        }

        return baseTolerance;
    }
}
