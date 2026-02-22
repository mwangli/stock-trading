package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.enums.RiskLevel;
import online.mwang.stockTrading.results.RiskCheckResult;
import online.mwang.stockTrading.entities.Position;
import online.mwang.stockTrading.services.RiskControlService;
import online.mwang.stockTrading.services.TradingRecordService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 风险控制服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskControlServiceImpl implements RiskControlService {

    private final TradingRecordService tradingRecordService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public RiskCheckResult checkBeforeBuy() {
        log.info("Checking risk before buy");
        
        // 检查单日止损
        if (isDailyStopLossTriggered()) {
            log.warn("Risk check failed: Daily stop loss triggered");
            return RiskCheckResult.fail("Daily stop loss triggered (-3%)");
        }
        
        // 检查月度熔断
        if (isMonthlyCircuitBreakerTriggered()) {
            log.warn("Risk check failed: Monthly circuit breaker triggered");
            return RiskCheckResult.fail("Monthly circuit breaker triggered (-10%)");
        }
        
        return RiskCheckResult.pass();
    }

    @Override
    public RiskCheckResult checkPositionForSell(Position position) {
        log.info("Checking position {} for sell", position.getStockCode());
        
        // 计算持仓盈亏
        double pnl = calculatePositionPnL(position);
        
        // 如果亏损超过3%，触发止损卖出
        if (pnl <= DAILY_STOP_LOSS) {
            return RiskCheckResult.forceSell(String.format("Stop loss triggered: %.2f%%", pnl * 100));
        }
        
        return RiskCheckResult.pass();
    }

    @Override
    public double calculateDailyPnL() {
        log.info("Calculating daily PnL");
        
        // 从Redis缓存获取当日盈亏
        String key = "risk:daily:pnl:" + LocalDate.now();
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            return (Double) cached;
        }
        
        // 模拟计算
        double pnl = (Math.random() - 0.5) * 0.06; // -3% 到 +3%
        redisTemplate.opsForValue().set(key, pnl, 1, TimeUnit.DAYS);
        
        return pnl;
    }

    @Override
    public double calculateMonthlyPnL() {
        log.info("Calculating monthly PnL");
        
        String key = "risk:monthly:pnl:" + LocalDate.now().getYear() + ":" + LocalDate.now().getMonthValue();
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            return (Double) cached;
        }
        
        // 模拟计算
        double pnl = (Math.random() - 0.4) * 0.15; // -6% 到 +9%
        redisTemplate.opsForValue().set(key, pnl, 31, TimeUnit.DAYS);
        
        return pnl;
    }

    @Override
    public RiskLevel getCurrentRiskLevel() {
        double dailyPnL = calculateDailyPnL();
        double monthlyPnL = calculateMonthlyPnL();
        
        if (monthlyPnL <= MONTHLY_CIRCUIT_BREAKER) {
            return RiskLevel.MONTHLY_CIRCUIT_BREAKER;
        } else if (dailyPnL <= DAILY_STOP_LOSS) {
            return RiskLevel.DAILY_STOP_LOSS;
        } else if (dailyPnL < -0.01 || monthlyPnL < -0.05) {
            return RiskLevel.WARNING;
        }
        return RiskLevel.NORMAL;
    }

    @Override
    public boolean isDailyStopLossTriggered() {
        return calculateDailyPnL() <= DAILY_STOP_LOSS;
    }

    @Override
    public boolean isMonthlyCircuitBreakerTriggered() {
        return calculateMonthlyPnL() <= MONTHLY_CIRCUIT_BREAKER;
    }

    @Override
    public List<Position> getStopLossPositions() {
        log.info("Getting stop loss positions");
        
        // 简化：返回空列表
        return new ArrayList<>();
    }

    @Override
    public void logRiskEvent(String message) {
        log.warn("RISK EVENT: {}", message);
        
        // 记录到Redis
        String key = "risk:events:" + LocalDate.now();
        redisTemplate.opsForList().rightPush(key, LocalDateTime.now() + " - " + message);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }

    @Override
    public void resetRiskStatus() {
        log.info("Resetting risk status");
        
        String dailyKey = "risk:daily:pnl:" + LocalDate.now();
        String monthlyKey = "risk:monthly:pnl:" + LocalDate.now().getYear() + ":" + LocalDate.now().getMonthValue();
        
        redisTemplate.delete(dailyKey);
        redisTemplate.delete(monthlyKey);
        
        logRiskEvent("Risk status manually reset");
    }

    private double calculatePositionPnL(Position position) {
        // 使用 avgCost 作为买入成本
        if (position.getAvgCost() == null || position.getAvgCost().doubleValue() == 0) {
            return 0.0;
        }
        return (position.getCurrentPrice().doubleValue() - position.getAvgCost().doubleValue()) / position.getAvgCost().doubleValue();
    }
}
