// AI_GENERATE_START -----
package com.stock.modelService.job;

import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmDataQualityConfig;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 收盘后 LSTM 离线训练任务。
 * 将全市场股票合并训练为一个共享基础模型，生产交易时不触发训练。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@Component("offlineLstmTrainingJob")
@RequiredArgsConstructor
public class OfflineLstmTrainingJob {

    private static final int TRAIN_DAYS = 1000;
    private static final int MODEL_MAX_AGE_DAYS = 30;

    private final StockInfoRepository stockInfoRepository;
    private final LstmTrainerService lstmTrainerService;
    private final LstmDataQualityConfig lstmDataQualityConfig;

    /**
     * 更新缺失或过期的全市场共享基础模型。
     * 固定训练窗口和模型有效期，避免引入在线参数优化和逐股模型维护。
     */
    public void trainStaleModels() {
        List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
                .filter(code -> !lstmDataQualityConfig.getSkipTrainingCodes().contains(code))
                .toList();
        if (lstmTrainerService.isSharedModelFresh(MODEL_MAX_AGE_DAYS)) {
            log.info("共享 LSTM 模型版本兼容且仍在 {} 天有效期内", MODEL_MAX_AGE_DAYS);
            return;
        }

        LstmTrainerService.TrainingResult result = lstmTrainerService.trainSharedModel(stockCodes, TRAIN_DAYS);
        if (result == null || !result.isSuccess()) {
            log.warn("收盘后共享 LSTM 训练未成功: stocks={}, reason={}", stockCodes.size(),
                    result == null ? "无返回结果" : result.getMessage());
            return;
        }
        log.info("收盘后共享 LSTM 训练完成: stocks={}, trainSamples={}, valSamples={}, modelPath={}",
                stockCodes.size(), result.getTrainSamples(), result.getValSamples(), result.getModelPath());
    }
}
// AI_GENERATE_END -----
