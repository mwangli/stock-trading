// AI_GENERATE_START ---
package com.stock.modelService.service;

import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * LSTM 在线推理门面。
 * 实际推理由 DJL + PyTorch Engine 执行，并从 MongoDB 激活版本读取 StockLSTMModel 参数。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Service
@RequiredArgsConstructor
public class LstmInferenceService {

    private final LstmTrainerService lstmTrainerService;

    /**
     * 预测单只股票下一交易日结果。
     *
     * @param stockCode 股票代码
     * @return LSTM 预测结果
     */
    public LstmPredictionResultDto predictNext(String stockCode) {
        return lstmTrainerService.predictNext(stockCode);
    }

    /**
     * 批量预测股票下一交易日结果。
     *
     * @param stockCodes 股票代码集合
     * @return 股票代码到预测结果的映射
     */
    public Map<String, LstmPredictionResultDto> predictNextBatch(List<String> stockCodes) {
        return lstmTrainerService.predictNextBatch(stockCodes);
    }

    /**
     * 判断是否存在可校验、可加载的激活模型。
     *
     * @return true 表示模型可用于推理
     */
    public boolean hasModel() {
        return lstmTrainerService.hasSharedModel();
    }
}
// AI_GENERATE_END ---
