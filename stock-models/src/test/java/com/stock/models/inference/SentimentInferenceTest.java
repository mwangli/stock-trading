package com.stock.models.inference;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SentimentInferenceTest {

    @Test
    public void testAnalyzePositiveNews() {
        SentimentInference inference = new SentimentInference();
        inference.loadModel();
        
        String positiveNews = "贵州茅台发布财报显示业绩大幅增长，预计全年营收突破1000亿，分析师推荐买入";
        
        SentimentInference.SentimentResult result = inference.analyze(positiveNews);
        
        assertNotNull(result, "分析结果不应为空");
        System.out.println("正面新闻情感分析: " + result.getLabel() + ", 分数: " + result.getScore());
        
        assertTrue(result.getScore() >= 0, "正面新闻应该得到正分");
    }

    @Test
    public void testAnalyzeNegativeNews() {
        SentimentInference inference = new SentimentInference();
        inference.loadModel();
        
        String negativeNews = "某公司发布业绩预警，预计亏损扩大，股价大幅下跌，风险加剧";
        
        SentimentInference.SentimentResult result = inference.analyze(negativeNews);
        
        assertNotNull(result, "分析结果不应为空");
        System.out.println("负面新闻情感分析: " + result.getLabel() + ", 分数: " + result.getScore());
        
        assertTrue(result.getScore() <= 0, "负面新闻应该得到负分");
    }

    @Test
    public void testAnalyzeNeutralNews() {
        SentimentInference inference = new SentimentInference();
        inference.loadModel();
        
        String neutralNews = "今日股市收盘，两市成交量共计5000亿元";
        
        SentimentInference.SentimentResult result = inference.analyze(neutralNews);
        
        assertNotNull(result, "分析结果不应为空");
        System.out.println("中性新闻情感分析: " + result.getLabel() + ", 分数: " + result.getScore());
    }

    @Test
    public void testAnalyzeEmptyText() {
        SentimentInference inference = new SentimentInference();
        inference.loadModel();
        
        SentimentInference.SentimentResult result = inference.analyze("");
        
        assertNotNull(result, "分析结果不应为空");
        assertEquals("neutral", result.getLabel(), "空文本应返回中性");
    }

    @Test
    public void testAnalyzeWithDetails() {
        SentimentInference inference = new SentimentInference();
        inference.loadModel();
        
        String news = "公司业绩增长50%，利好消息推动股价上涨";
        
        Map<String, Object> details = inference.analyzeWithDetails(news);
        
        assertNotNull(details, "详细信息不应为空");
        assertTrue(details.containsKey("label"), "应包含label字段");
        assertTrue(details.containsKey("score"), "应包含score字段");
        assertTrue(details.containsKey("confidence"), "应包含confidence字段");
        assertTrue(details.containsKey("probabilities"), "应包含probabilities字段");
        
        System.out.println("详细分析结果: " + details);
    }

    @Test
    public void testModelNotLoaded() {
        SentimentInference inference = new SentimentInference();
        
        SentimentInference.SentimentResult result = inference.analyze("测试文本");
        
        assertNotNull(result, "结果不应为空");
        assertEquals("neutral", result.getLabel(), "模型未加载时应返回中性");
    }
}
