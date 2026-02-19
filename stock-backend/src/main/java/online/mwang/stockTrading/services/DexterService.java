package online.mwang.stockTrading.services;

import online.mwang.stockTrading.models.DexterResult;

import java.util.List;

/**
 * Dexter金融分析服务
 * 对接Dexter Agent获取基本面分析和走势建议
 */
public interface DexterService {

    /**
     * 获取个股财务分析
     * @param stockCode 股票代码
     * @return 分析结果
     */
    DexterResult analyzeStock(String stockCode);

    /**
     * 获取次日走势建议
     * @param stockCode 股票代码
     * @return 走势建议
     */
    DexterResult getNextDayAdvice(String stockCode);

    /**
     * 批量分析多只股票
     * @param stockCodes 股票代码列表
     * @return 分析结果列表
     */
    List<DexterResult> analyzeBatch(List<String> stockCodes);

    /**
     * 将Dexter建议转换为量化评分
     * @param advice 建议文本
     * @return 评分 (0-1)
     */
    double adviceToScore(String advice);

    /**
     * 获取所有股票的Dexter评分排名
     * @return 按评分排序的股票列表
     */
    List<StockDexterScore> getStockDexterRanking();

    /**
     * 查询个股详细信息
     * @param stockCode 股票代码
     * @return 详细信息
     */
    String getStockDetail(String stockCode);

    /**
     * 数据类：股票Dexter评分
     */
    class StockDexterScore {
        private String stockCode;
        private String stockName;
        private double score;
        private String advice;
        private String analysis;

        public StockDexterScore(String stockCode, double score) {
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
        public String getAdvice() { return advice; }
        public void setAdvice(String advice) { this.advice = advice; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
    }
}
