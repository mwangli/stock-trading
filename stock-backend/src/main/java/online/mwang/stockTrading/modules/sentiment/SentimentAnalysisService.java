package online.mwang.stockTrading.modules.sentiment;

import online.mwang.stockTrading.modules.sentiment.model.SentimentResult;

import java.util.List;

/**
 * 情感分析服务
 * 基于FinBERT对财经新闻进行情感分析
 */
public interface SentimentAnalysisService {

    /**
     * 分析单条文本情感
     * @param text 文本内容
     * @return 情感分析结果
     */
    SentimentResult analyze(String text);

    /**
     * 批量分析文本情感
     * @param texts 文本列表
     * @return 情感分析结果列表
     */
    List<SentimentResult> batchAnalyze(List<String> texts);

    /**
     * 计算指定股票的情感得分
     * 基于该股票相关新闻的情感分析，时间加权
     * @param stockCode 股票代码
     * @return 情感得分（-1到1）
     */
    double calculateStockSentiment(String stockCode);

    /**
     * 获取所有股票的情感得分排名
     * @return 按情感得分排序的股票列表
     */
    List<StockSentimentScore> getStockSentimentRanking();

    /**
     * 采集并分析指定股票的新闻
     * @param stockCode 股票代码
     * @return 分析后的情感结果
     */
    SentimentResult fetchAndAnalyzeNews(String stockCode);

    /**
     * 数据类：股票情感得分
     */
    class StockSentimentScore {
        private String stockCode;
        private String stockName;
        private double score;
        private int newsCount;

        public StockSentimentScore(String stockCode, double score) {
            this.stockCode = stockCode;
            this.score = score;
        }

        // Getters and setters
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public String getStockName() { return stockName; }
        public void setStockName(String stockName) { this.stockName = stockName; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public int getNewsCount() { return newsCount; }
        public void setNewsCount(int newsCount) { this.newsCount = newsCount; }
    }
}
