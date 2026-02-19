package online.mwang.stockTrading.modules.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.risk.enums.RiskLevel;
import online.mwang.stockTrading.modules.risk.model.RiskCheckResult;
import online.mwang.stockTrading.modules.trading.entity.Position;
import online.mwang.stockTrading.modules.trading.mapper.TradingRecordMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    private final TradingRecordMapper tradingRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public RiskCheckResult checkBeforeBuy() {
        log.info("Checking risk before buy");
        
        RiskCheckResult result = new RiskCheckResult();
        
        // 检查单日止损
        if (isDailyStopLossTriggered()) {
            result.setAllowed(false);
            result.setRiskLevel(RiskLevel.HIGH);
            result.setMessage("Daily stop loss triggered (-3%)");
            log.warn("Risk check failed: Daily stop loss triggered");
            return result;
        }
        
        // 检查月度熔断
        if (isMonthlyCircuitBreakerTriggered()) {
            result.setAllowed(false);
            result.setRiskLevel(RiskLevel.CRITICAL);
            result.setMessage("Monthly circuit breaker triggered (-10%)");
            log.warn("Risk check failed: Monthly circuit breaker triggered");
            return result;
        }
        
        result.setAllowed(true);
        result.setRiskLevel(getCurrentRiskLevel());
        result.setMessage("Risk check passed");
        return result;
    }

    @Override
    public RiskCheckResult checkPositionForSell(Position position) {
        log.info("Checking position {} for sell", position.getCode());
        
        RiskCheckResult result = new RiskCheckResult();
        
        // 计算持仓盈亏
        double pnl = calculatePositionPnL(position);
        
        // 如果亏损超过3%，触发止损
        if (pnl <= DAILY_STOP_LOSS) {
            result.setAllowed(true);
            result.setRiskLevel(RiskLevel.HIGH);
            result.setMessage(String.format("Stop loss triggered: %.2f%%", pnl * 100));
            result.setAction(online.mwang.stockTrading.modules.risk.enums.Action.SELL);
            return result;
        }
        
        result.setAllowed(true);
        result.setRiskLevel(RiskLevel.NORMAL);
        result.setMessage("Position normal");
        return result;
    }

    @Override
    public double calculateDailyPnL() {
        log.info("Calculating daily PnL");
        
        // 简化：从Redis缓存获取当日盈亏
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
            return RiskLevel.CRITICAL;
        } else if (dailyPnL <= DAILY_STOP_LOSS) {
            return RiskLevel.HIGH;
        } else if (dailyPnL < -0.01 || monthlyPnL < -0.05) {
            return RiskLevel.MEDIUM;
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
        // 简化计算
        return (position.getCurrentPrice() - position.getBuyPrice()) / position.getBuyPrice();
    }
}
