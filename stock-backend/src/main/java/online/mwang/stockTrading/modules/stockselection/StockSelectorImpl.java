package online.mwang.stockTrading.modules.stockselection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.stockselection.model.SelectResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 综合选股服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSelectorImpl implements StockSelector {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "stock:selection:";

    @Override
    public SelectResult selectBestStock() {
        log.info("Selecting best stock");
        
        List<ComprehensiveScore> rankings = getComprehensiveRanking();
        if (rankings.isEmpty()) {
            return SelectResult.empty();
        }
        
        ComprehensiveScore best = rankings.get(0);
        List<ComprehensiveScore> top3 = rankings.stream().limit(3).collect(Collectors.toList());
        
        return SelectResult.builder()
            .selectDate(new Date())
            .best(best)
            .top3(top3)
            .allRankings(rankings)
            .selectionTime(0)
            .build();
    }

    @Override
    public List<ComprehensiveScore> getComprehensiveRanking() {
        log.info("Getting comprehensive ranking");
        
        List<StockInfo> candidates = getCandidateStocks();
        List<ComprehensiveScore> scores = new ArrayList<>();
        
        for (StockInfo stock : candidates) {
            ComprehensiveScore score = calculateScore(stock);
            scores.add(score);
        }
        
        scores.sort(Comparator.comparing(ComprehensiveScore::getAvgRank));
        return scores;
    }

    @Override
    public SelectResult getTodaySelection() {
        String cacheKey = CACHE_PREFIX + "today";
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (SelectResult) cached;
        }
        
        SelectResult result = selectBestStock();
        redisTemplate.opsForValue().set(cacheKey, result, 1, TimeUnit.DAYS);
        return result;
    }

    @Override
    public void saveSelection(SelectResult result) {
        String key = CACHE_PREFIX + "history:" + result.getSelectDate();
        redisTemplate.opsForList().rightPush(key, result);
    }

    @Override
    public List<SelectResult> getHistorySelections(int days) {
        return new ArrayList<>();
    }

    private ComprehensiveScore calculateScore(StockInfo stock) {
        double lstmScore = Math.random() * 0.7 + 0.15;
        double sentimentScore = Math.random() * 0.6 + 0.2;
        double dexterScore = Math.random() * 0.6 + 0.2;
        
        int lstmRank = (int)(Math.random() * 50) + 1;
        int sentimentRank = (int)(Math.random() * 50) + 1;
        int dexterRank = (int)(Math.random() * 50) + 1;
        double avgRank = (lstmRank + sentimentRank + dexterRank) / 3.0;
        
        ComprehensiveScore score = new ComprehensiveScore(
            stock.getCode(), stock.getName(), avgRank,
            lstmRank, sentimentRank, dexterRank
        );
        
        score.setLstmScore(lstmScore);
        score.setSentimentScore(sentimentScore);
        score.setDexterScore(dexterScore);
        
        return score;
    }

    private List<StockInfo> getCandidateStocks() {
        List<StockInfo> candidates = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            StockInfo stock = new StockInfo();
            stock.setId((long) i);
            stock.setCode(String.format("%06d", i));
            stock.setName("股票" + i);
            stock.setMarket(i % 2 == 0 ? "SH" : "SZ");
            candidates.add(stock);
        }
        return candidates;
    }
}
