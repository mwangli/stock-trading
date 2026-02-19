package online.mwang.stockTrading.modules.decision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.decision.enums.Signal;
import online.mwang.stockTrading.modules.decision.model.TradingSignal;
import online.mwang.stockTrading.modules.risk.RiskControlService;
import online.mwang.stockTrading.modules.stockselection.StockSelector;
import online.mwang.stockTrading.modules.stockselection.model.SelectResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 决策引擎实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionEngineImpl implements DecisionEngine {

    private final StockSelector stockSelector;
    private final RiskControlService riskControlService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final double BUY_THRESHOLD = 0.7;

    @Override
    public TradingSignal generateSignal(SelectResult selectResult) {
        log.info("Generating signal");
        
        if (selectResult.getBest() == null) {
            return TradingSignal.hold("No best stock selected");
        }
        
        // 检查风控
        if (!riskControlService.checkBeforeBuy().isPassed()) {
            return TradingSignal.hold("Risk control triggered");
        }
        
        StockSelector.ComprehensiveScore best = selectResult.getBest();
        double score = (best.getLstmScore() + best.getSentimentScore() + best.getDexterScore()) / 3.0;
        
        if (score >= BUY_THRESHOLD) {
            return TradingSignal.buy(
                best.getStockCode(),
                best.getStockName(),
                score,
                String.format("High comprehensive score: %.2f", score)
            );
        }
        
        return TradingSignal.hold(String.format("Score %.2f below threshold", score));
    }

    @Override
    public boolean shouldBuy(StockSelector.ComprehensiveScore score) {
        double avgScore = (score.getLstmScore() + score.getSentimentScore() + score.getDexterScore()) / 3.0;
        return avgScore >= BUY_THRESHOLD;
    }

    @Override
    public int calculateBuyQuantity(double availableAmount, String stockCode, double price) {
        if (price <= 0) return 0;
        double maxAmount = availableAmount * 0.2;
        return (int) (maxAmount / price / 100) * 100;
    }

    @Override
    public TradingSignal getTodaySignal() {
        SelectResult today = stockSelector.getTodaySelection();
        return generateSignal(today);
    }

    @Override
    public void saveSignal(TradingSignal signal) {
        String key = "signal:" + signal.getStockCode() + ":" + new Date();
        redisTemplate.opsForValue().set(key, signal, 7, TimeUnit.DAYS);
    }

    @Override
    public boolean isSignalValid(TradingSignal signal) {
        if (signal.getGenerateTime() == null) return false;
        Date today = new Date();
        return isSameDay(signal.getGenerateTime(), today);
    }

    private boolean isSameDay(Date date1, Date date2) {
        return date1.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .equals(date2.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
    }

    public List<TradingSignal> generateSignalsForTopStocks(int count) {
        List<TradingSignal> signals = new ArrayList<>();
        SelectResult result = stockSelector.selectBestStock();
        
        if (result != null && result.getTop3() != null) {
            for (StockSelector.ComprehensiveScore score : result.getTop3()) {
                SelectResult single = SelectResult.builder()
                    .selectDate(new Date())
                    .best(score)
                    .build();
                TradingSignal signal = generateSignal(single);
                signals.add(signal);
                saveSignal(signal);
            }
        }
        
        return signals;
    }
}
