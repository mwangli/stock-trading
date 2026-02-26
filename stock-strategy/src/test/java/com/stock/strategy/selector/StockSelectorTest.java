package com.stock.strategy.selector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StockSelectorTest {

    @Test
    public void testSelectStocks() {
        StockSelector selector = new StockSelector();
        
        List<StockSelector.StockRecommendation> recommendations = selector.select(3);
        
        assertNotNull(recommendations, "推荐列表不应为空");
        assertTrue(recommendations.size() <= 3, "推荐数量不应超过限制");
        
        System.out.println("选股结果:");
        for (StockSelector.StockRecommendation rec : recommendations) {
            System.out.println("股票: " + rec.getStockCode() + 
                ", 综合得分: " + rec.getScore() + 
                ", LSTM得分: " + rec.getLstmScore() + 
                ", 情感得分: " + rec.getSentimentScore());
        }
        
        assertTrue(recommendations.size() > 0, "应该返回推荐股票");
    }

    @Test
    public void testSelectWithLimit() {
        StockSelector selector = new StockSelector();
        
        List<StockSelector.StockRecommendation> recommendations = selector.select(5);
        
        assertNotNull(recommendations, "推荐列表不应为空");
        assertTrue(recommendations.size() <= 5, "推荐数量应限制为5");
    }

    @Test
    public void testScoreCalculation() {
        StockSelector selector = new StockSelector();
        
        List<StockSelector.StockRecommendation> recommendations = selector.select(10);
        
        if (recommendations.size() >= 2) {
            double firstScore = recommendations.get(0).getScore();
            double secondScore = recommendations.get(1).getScore();
            
            System.out.println("第一得分: " + firstScore + ", 第二得分: " + secondScore);
            
            assertTrue(firstScore >= secondScore, "得分应该按降序排列");
        }
    }

    @Test
    public void testRecommendationFields() {
        StockSelector selector = new StockSelector();
        
        List<StockSelector.StockRecommendation> recommendations = selector.select(1);
        
        if (!recommendations.isEmpty()) {
            StockSelector.StockRecommendation rec = recommendations.get(0);
            
            assertNotNull(rec.getStockCode(), "股票代码不应为空");
            assertNotNull(rec.getScore(), "综合得分不应为空");
            assertNotNull(rec.getLstmScore(), "LSTM得分不应为空");
            assertNotNull(rec.getSentimentScore(), "情感得分不应为空");
            
            System.out.println("推荐股票: " + rec.getStockCode() + 
                ", 综合得分: " + rec.getScore());
        }
    }
}
