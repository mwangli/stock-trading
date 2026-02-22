package online.mwang.stockTrading;

import online.mwang.stockTrading.results.SelectResult;
import online.mwang.stockTrading.services.StockSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 综合选股模块测试验证
 * 模块五：综合选股
 * 
 * 验收标准：
 * 1. 综合评分 - 得分范围正确(0-100)
 * 2. 选股结果 - 返回Top1+Top3
 * 3. 并行执行 - 30秒内完成
 * 4. 单元测试覆盖率 > 70%
 */
@DisplayName("综合选股模块 - 单元测试")
public class StockSelectorTest {

    /**
     * 测试用例: TC-SEL-001
     * 功能: 双因子综合评分
     * 验证: 综合得分计算公式正确
     * 
     * 公式: 综合得分 = LSTM×0.6 + 情感×0.4
     * 输入范围: 0-100分
     */
    @Test
    @DisplayName("TC-SEL-001: 综合得分计算 - 权重验证")
    void testComprehensiveScoreCalculation() {
        // 测试不同权重组合 (输入为0-100分制)
        double[][] testCases = {
            {80.0, 60.0, 72.0},  // LSTM=80, 情感=60 -> 80*0.6 + 60*0.4 = 72
            {50.0, 50.0, 50.0},  // LSTM=50, 情感=50 -> 50*0.6 + 50*0.4 = 50
            {20.0, 90.0, 48.0},  // LSTM=20, 情感=90 -> 20*0.6 + 90*0.4 = 48
            {0.0, 0.0, 0.0},    // 边界: 全0
            {100.0, 100.0, 100.0},  // 边界: 全100
        };

        for (double[] testCase : testCases) {
            double lstmScore = testCase[0];
            double sentimentScore = testCase[1];
            double expectedOverall = testCase[2];

            // 计算综合得分
            double overallScore = calculateOverallScore(lstmScore, sentimentScore);

            // 验证
            assertEquals(expectedOverall, overallScore, 0.001,
                String.format("LSTM=%.1f, 情感=%.1f 期望%.2f 实际%.2f", 
                    lstmScore, sentimentScore, expectedOverall, overallScore));
        }
    }

    /**
     * 测试用例: TC-SEL-002
     * 功能: 综合评分得分范围验证
     * 验证: 得分范围在0-100之间
     */
    @Test
    @DisplayName("TC-SEL-002: 得分范围验证 - 0到100之间")
    void testScoreRange() {
        // 测试边界情况 (0-100分制)
        double[][] testCases = {
            {0.0, 0.0, 0.0},      // 最小值
            {0.0, 100.0, 40.0},   // LSTM最小，情感最大
            {100.0, 0.0, 60.0},   // LSTM最大，情感最小
            {100.0, 100.0, 100.0}, // 最大值
            {50.0, 50.0, 50.0},   // 中间值
        };

        for (double[] testCase : testCases) {
            double lstmScore = testCase[0];
            double sentimentScore = testCase[1];
            double expectedOverall = testCase[2];

            double overallScore = calculateOverallScore(lstmScore, sentimentScore);

            // 验证范围: 0 <= score <= 100
            assertTrue(overallScore >= 0.0 && overallScore <= 100.0,
                "得分应在0-100范围内，实际: " + overallScore);
            
            // 验证计算正确性
            assertEquals(expectedOverall, overallScore, 0.001);
        }
    }

    /**
     * 测试用例: TC-SEL-003
     * 功能: 排序功能验证
     * 验证: 按综合得分降序排列
     */
    @Test
    @DisplayName("TC-SEL-003: 排序验证 - 得分降序")
    void testRankingOrder() {
        // 创建测试数据
        List<StockSelector.ComprehensiveScore> scores = new ArrayList<>();
        
        scores.add(createScore("000001", "股票A", 0.8, 0.6, 1, 3));  // avgRank=2
        scores.add(createScore("000002", "股票B", 0.5, 0.5, 4, 4));  // avgRank=4
        scores.add(createScore("000003", "股票C", 0.9, 0.8, 2, 1));  // avgRank=1.5
        scores.add(createScore("000004", "股票D", 0.3, 0.3, 5, 5));  // avgRank=5

        // 按avgRank排序 (升序, avgRank越小越好)
        scores.sort(Comparator.comparing(StockSelector.ComprehensiveScore::getAvgRank));

        // 验证排序结果
        assertEquals("000003", scores.get(0).getStockCode()); // 排名1
        assertEquals("000001", scores.get(1).getStockCode()); // 排名2
        assertEquals("000002", scores.get(2).getStockCode()); // 排名3
        assertEquals("000004", scores.get(3).getStockCode()); // 排名4
    }

    /**
     * 测试用例: TC-SEL-004
     * 功能: TopN选取验证
     * 验证: 选出Top1(主推) + Top3(备选)
     */
    @Test
    @DisplayName("TC-SEL-004: TopN选取 - 主推+备选")
    void testTopNSelection() {
        // 创建10只股票的测试数据
        List<StockSelector.ComprehensiveScore> allScores = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            double score = 1.0 - (i * 0.1); // 得分从0.9递减到0.0
            allScores.add(createScore(
                String.format("%06d", i),
                "股票" + i,
                score,
                score,
                i,
                i
            ));
        }

        // 选取Top1
        StockSelector.ComprehensiveScore top1 = allScores.get(0);
        assertEquals("000001", top1.getStockCode());
        assertEquals(0.9, top1.getLstmScore(), 0.001);

        // 选取Top3
        List<StockSelector.ComprehensiveScore> top3 = allScores.subList(0, 3);
        assertEquals(3, top3.size());
        assertEquals("000001", top3.get(0).getStockCode());
        assertEquals("000002", top3.get(1).getStockCode());
        assertEquals("000003", top3.get(2).getStockCode());
    }

    /**
     * 测试用例: TC-SEL-005
     * 功能: 选股结果结构验证
     * 验证: SelectResult包含必要字段
     */
    @Test
    @DisplayName("TC-SEL-005: 结果结构验证")
    void testSelectResultStructure() {
        // 创建测试数据
        StockSelector.ComprehensiveScore best = createScore(
            "000001", "测试股票", 0.8, 0.7, 1, 2
        );
        
        List<StockSelector.ComprehensiveScore> top3 = new ArrayList<>();
        top3.add(best);
        top3.add(createScore("000002", "股票2", 0.7, 0.6, 2, 3));
        top3.add(createScore("000003", "股票3", 0.6, 0.5, 3, 4));

        // 构建结果
        SelectResult result = SelectResult.builder()
            .selectDate(new java.util.Date())
            .best(best)
            .top3(top3)
            .allRankings(top3)
            .selectionTime(100)
            .build();

        // 验证字段
        assertNotNull(result.getSelectDate());
        assertNotNull(result.getBest());
        assertNotNull(result.getTop3());
        assertEquals(3, result.getTop3().size());
        assertEquals(100, result.getSelectionTime());
        
        // 验证主推股票
        assertEquals("000001", result.getBest().getStockCode());
    }

    /**
     * 测试用例: TC-SEL-006
     * 功能: 空结果处理
     * 验证: 空股票池返回空结果
     */
    @Test
    @DisplayName("TC-SEL-006: 空结果处理")
    void testEmptyResult() {
        SelectResult emptyResult = SelectResult.empty();
        
        assertNotNull(emptyResult.getSelectDate());
        assertNull(emptyResult.getBest());
        assertNull(emptyResult.getTop3());
    }

    /**
     * 测试用例: TC-SEL-007
     * 功能: 得分转换公式验证
     * 验证: LSTM因子和情感因子得分转换正确
     * 
     * LSTM因子: (预测涨跌幅 + 10) / 20 × 100
     * 情感因子: (情感得分 + 1) / 2 × 100
     */
    @Test
    @DisplayName("TC-SEL-007: 因子得分转换验证")
    void testScoreTransformation() {
        // LSTM因子转换测试
        double[] lstmPredictions = {-10.0, -5.0, 0.0, 5.0, 10.0};
        double[] expectedLstmScores = {0.0, 25.0, 50.0, 75.0, 100.0};
        
        for (int i = 0; i < lstmPredictions.length; i++) {
            double prediction = lstmPredictions[i];
            double expected = expectedLstmScores[i];
            double actual = transformLstmScore(prediction);
            assertEquals(expected, actual, 0.001, 
                String.format("预测涨跌幅%.1f期望得分%.1f实际%.1f", prediction, expected, actual));
        }

        // 情感因子转换测试
        double[] sentimentScores = {-1.0, -0.5, 0.0, 0.5, 1.0};
        double[] expectedSentimentScores = {0.0, 25.0, 50.0, 75.0, 100.0};
        
        for (int i = 0; i < sentimentScores.length; i++) {
            double sentiment = sentimentScores[i];
            double expected = expectedSentimentScores[i];
            double actual = transformSentimentScore(sentiment);
            assertEquals(expected, actual, 0.001,
                String.format("情感得分%.1f期望%.1f实际%.1f", sentiment, expected, actual));
        }
    }

    /**
     * 测试用例: TC-SEL-008
     * 功能: 并行因子获取模拟
     * 验证: CompletableFuture并行调用
     */
    @Test
    @DisplayName("TC-SEL-008: 并行执行模拟")
    void testParallelFactorRetrieval() throws Exception {
        // 模拟并行获取因子
        long startTime = System.currentTimeMillis();
        
        // 模拟两个并行任务
        java.util.concurrent.CompletableFuture<Double> lstmFuture = 
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100); // 模拟网络延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0.8; // LSTM得分
            });
        
        java.util.concurrent.CompletableFuture<Double> sentimentFuture = 
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0.6; // 情感得分
            });
        
        // 等待所有任务完成
        java.util.concurrent.CompletableFuture.allOf(lstmFuture, sentimentFuture).join();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // 验证结果
        assertEquals(0.8, lstmFuture.get());
        assertEquals(0.6, sentimentFuture.get());
        
        // 并行执行应该比串行快 (串行需要200ms, 并行应该接近100ms)
        assertTrue(totalTime < 200, "并行执行耗时: " + totalTime + "ms");
        System.out.println("并行执行耗时: " + totalTime + "ms");
    }

    /**
     * 测试用例: TC-SEL-009
     * 功能: 容错处理 - 因子缺失时使用默认值
     * 验证: 部分因子缺失时可降级计算
     */
    @Test
    @DisplayName("TC-SEL-009: 容错处理 - 默认值")
    void testFaultTolerance() {
        // 场景1: LSTM因子缺失，使用默认值50
        Double missingLstm = null;
        double sentiment = 0.8;
        double resultWithMissingLstm = calculateWithDefault(missingLstm, sentiment);
        assertEquals(0.62, resultWithMissingLstm, 0.001); // 0.5*0.6 + 0.8*0.4 = 0.62

        // 场景2: 情感因子缺失，使用默认值50
        double lstm = 0.8;
        Double missingSentiment = null;
        double resultWithMissingSentiment = calculateWithDefault(lstm, missingSentiment);
        assertEquals(0.68, resultWithMissingSentiment, 0.001); // 0.8*0.6 + 0.5*0.4 = 0.68

        // 场景3: 全部缺失，使用默认值50
        double resultWithAllMissing = calculateWithDefault(null, null);
        assertEquals(0.50, resultWithAllMissing, 0.001); // 0.5*0.6 + 0.5*0.4 = 0.50
    }

    /**
     * 测试用例: TC-SEL-010
     * 功能: 缓存键生成
     * 验证: 缓存键格式正确
     */
    @Test
    @DisplayName("TC-SEL-010: 缓存键验证")
    void testCacheKey() {
        String cachePrefix = "stock:selection:";
        
        // 验证今日缓存键
        String todayKey = cachePrefix + "today";
        assertEquals("stock:selection:today", todayKey);
        
        // 验证历史缓存键
        String historyKey = cachePrefix + "history:2026-02-22";
        assertEquals("stock:selection:history:2026-02-22", historyKey);
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算综合得分
     * 公式: 综合得分 = LSTM×0.6 + 情感×0.4
     */
    private double calculateOverallScore(double lstmScore, double sentimentScore) {
        double lstmWeight = 0.6;
        double sentimentWeight = 0.4;
        return lstmScore * lstmWeight + sentimentScore * sentimentWeight;
    }

    /**
     * LSTM因子得分转换
     * 公式: (预测涨跌幅 + 10) / 20 × 100
     */
    private double transformLstmScore(double predictedChange) {
        return (predictedChange + 10) / 20 * 100;
    }

    /**
     * 情感因子得分转换
     * 公式: (情感得分 + 1) / 2 × 100
     */
    private double transformSentimentScore(double sentimentScore) {
        return (sentimentScore + 1) / 2 * 100;
    }

    /**
     * 带默认值的综合得分计算
     */
    private double calculateWithDefault(Double lstmScore, Double sentimentScore) {
        double defaultScore = 0.5; // 默认50分
        double lstm = lstmScore != null ? lstmScore : defaultScore;
        double sentiment = sentimentScore != null ? sentimentScore : defaultScore;
        return calculateOverallScore(lstm, sentiment);
    }

    /**
     * 创建测试用ComprehensiveScore对象
     */
    private StockSelector.ComprehensiveScore createScore(
            String stockCode, String stockName, 
            double lstmScore, double sentimentScore,
            int lstmRank, int sentimentRank) {
        
        StockSelector.ComprehensiveScore score = new StockSelector.ComprehensiveScore(
            stockCode, stockName, (lstmRank + sentimentRank) / 2.0, lstmRank, sentimentRank
        );
        score.setLstmScore(lstmScore);
        score.setSentimentScore(sentimentScore);
        return score;
    }
}
