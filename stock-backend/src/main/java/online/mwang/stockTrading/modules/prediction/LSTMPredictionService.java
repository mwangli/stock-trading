package online.mwang.stockTrading.modules.prediction;

import online.mwang.stockTrading.domain.entity.StockPrices;
import online.mwang.stockTrading.modules.prediction.model.PredictionResult;

import java.util.List;

/**
 * LSTM预测服务
 * 基于PyTorch LSTM模型预测次日涨跌
 */
public interface LSTMPredictionService {

    /**
     * 预测指定股票次日涨跌概率
     * @param stockCode 股票代码
     * @return 预测结果
     */
    PredictionResult predict(String stockCode);

    /**
     * 批量预测多只股票
     * @param stockCodes 股票代码列表
     * @return 预测结果列表
     */
    List<PredictionResult> predictBatch(List<String> stockCodes);

    /**
     * 获取所有股票的LSTM预测排名
     * @return 按上涨概率排序的股票列表
     */
    List<StockPredictionScore> getStockPredictionRanking();

    /**
     * 训练模型
     * @param historyPrices 历史价格数据
     * @return 测试集上的预测结果
     */
    List<StockPrices> trainModel(List<StockPrices> historyPrices);

    /**
     * 重新训练所有股票的模型
     */
    void retrainAllModels();

    /**
     * 评估模型准确率
     * @param testData 测试数据
     * @return 准确率
     */
    double evaluateModel(List<StockPrices> testData);

    /**
     * 数据类：股票预测得分
     */
    class StockPredictionScore {
        private String stockCode;
        private String stockName;
        private double upProbability;
        private double predictedChange;

        public StockPredictionScore(String stockCode, double upProbability) {
            this.stockCode = stockCode;
            this.upProbability = upProbability;
        }

        // Getters and setters
        public String getStockCode() { return stockCode; }
        public void setStockCode(String stockCode) { this.stockCode = stockCode; }
        public String getStockName() { return stockName; }
        public void setStockName(String stockName) { this.stockName = stockName; }
        public double getUpProbability() { return upProbability; }
        public void setUpProbability(double upProbability) { this.upProbability = upProbability; }
        public double getPredictedChange() { return predictedChange; }
        public void setPredictedChange(double predictedChange) { this.predictedChange = predictedChange; }
    }
}
