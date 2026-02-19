package online.mwang.stockTrading.services;

import online.mwang.stockTrading.models.SelectResult;

import java.util.List;

/**
 * 综合选股服务
 * 综合LSTM、情感分析、Dexter三方评分选股
 */
public interface StockSelector {

    /**
     * 综合评分选股，选出最佳股票
     * 权重：LSTM 1/3 + 情感 1/3 + Dexter 1/3
     * @return 选股结果
     */
    SelectResult selectBestStock();

    /**
     * 获取综合评分排名
     * @return 按综合评分排序的股票列表
     */
    List<ComprehensiveScore> getComprehensiveRanking();

    /**
     * 获取今日选股结果（从缓存或重新计算）
     * @return 选股结果
     */
    SelectResult getTodaySelection();

    /**
     * 保存选股结果
     * @param result 选股结果
     */
    void saveSelection(SelectResult result);

    /**
     * 获取历史选股记录
     * @param days 最近N天
     * @return 历史选股列表
     */
    List<SelectResult> getHistorySelections(int days);

    /**
     * 数据类：综合评分
     */
    class ComprehensiveScore {
        private String stockCode;
        private String stockName;
        private double avgRank;          // 平均排名（越小越好）
        private int lstmRank;            // LSTM排名
        private int sentimentRank;       // 情感排名
        private int dexterRank;          // Dexter排名
        private double lstmScore;        // LSTM得分
        private double sentimentScore;   // 情感得分
        private double dexterScore;      // Dexter得分

        public ComprehensiveScore(String stockCode, String stockName, double avgRank, 
                                   int lstmRank, int sentimentRank, int dexterRank) {
            this.stockCode = stockCode;
            this.stockName = stockName;
            this.avgRank = avgRank;
            this.lstmRank = lstmRank;
            this.sentimentRank = sentimentRank;
            this.dexterRank = dexterRank;
        }

        // Getters and setters
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public String getStockName() { return stockName; }
        public void setStockName(String stockName) { this.stockName = stockName; }
        public double getAvgRank() { return avgRank; }
        public void setAvgRank(double avgRank) { this.avgRank = avgRank; }
        public int getLstmRank() { return lstmRank; }
        public void setLstmRank(int lstmRank) { this.lstmRank = lstmRank; }
        public int getSentimentRank() { return sentimentRank; }
        public void setSentimentRank(int sentimentRank) { this.sentimentRank = sentimentRank; }
        public int getDexterRank() { return dexterRank; }
        public void setDexterRank(int dexterRank) { this.dexterRank = dexterRank; }
        public double getLstmScore() { return lstmScore; }
        public void setLstmScore(double lstmScore) { this.lstmScore = lstmScore; }
        public double getSentimentScore() { return sentimentScore; }
        public void setSentimentScore(double sentimentScore) { this.sentimentScore = sentimentScore; }
        public double getDexterScore() { return dexterScore; }
        public void setDexterScore(double dexterScore) { this.dexterScore = dexterScore; }
    }
}
