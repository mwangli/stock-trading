// AI_GENERATE_START --------
package com.stock.modelService.job;

import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmDataQualityConfig;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.persistence.LstmModelRepository;
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
    private final LstmTrainingConfig lstmTrainingConfig;
    private final LstmModelRepository lstmModelRepository;

    /**
     * 更新缺失或过期的全市场共享基础模型。
     * 固定训练窗口和模型有效期，避免引入在线参数优化和逐股模型维护。
     */
    public void trainStaleModels() {
        if (!lstmTrainingConfig.isTrainingEnabled()) {
            log.info("当前节点未启用 LSTM 离线训练，跳过共享模型更新");
            return;
        }
        List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
                .filter(code -> lstmDataQualityConfig.getSkipTrainingCodes() == null
                        || !lstmDataQualityConfig.getSkipTrainingCodes().contains(code))
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
        try {
            String modelPath = result.getModelPath();
            if (modelPath == null || !modelPath.startsWith("mongo:") || modelPath.length() <= 6) {
                throw new IllegalStateException("训练结果缺少候选模型版本 ID");
            }
            String candidateVersionId = modelPath.substring(6);
            lstmTrainerService.validateModelVersion(candidateVersionId);
            LstmModelDocument candidate = lstmModelRepository.findById(candidateVersionId)
                    .orElseThrow(() -> new IllegalStateException("候选模型版本不存在"));
            candidate.setStatus("READY");
            lstmModelRepository.save(candidate);
            log.info("收盘后共享 LSTM 候选模型已通过校验，等待人工激活: stocks={}, trainSamples={}, valSamples={}, versionId={}",
                    stockCodes.size(), result.getTrainSamples(), result.getValSamples(), candidateVersionId);
        } catch (RuntimeException exception) {
            log.error("收盘后共享 LSTM 候选模型校验失败: modelPath={}", result.getModelPath(), exception);
        }
    }
}
// AI_GENERATE_END --------
