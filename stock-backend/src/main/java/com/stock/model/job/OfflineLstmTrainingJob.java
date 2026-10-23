// AI_GENERATE_START-------------
package com.stock.model.job;

import com.stock.model.service.LstmModelRuntimeService;
import com.stock.model.service.ModelOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    private static final int MODEL_MAX_AGE_DAYS = 30;

    private final LstmModelRuntimeService modelRuntimeService;
    private final ModelOperationsService modelOperationsService;

    /**
     * 更新缺失或过期的全市场共享基础模型。
     * 固定训练窗口和模型有效期，避免引入在线参数优化和逐股模型维护。
     */
    public void trainStaleModels() {
        if (modelRuntimeService.isSharedModelFresh(MODEL_MAX_AGE_DAYS)) {
            log.info("共享 LSTM 模型版本兼容且仍在 {} 天有效期内", MODEL_MAX_AGE_DAYS);
            return;
        }
        var result = modelOperationsService.startScheduledTraining();
        log.info("收盘后共享 LSTM 训练检查完成: operationType={}, operationId={}, message={}",
                result.getOperationType(), result.getOperationId(), result.getMessage());
    }
}
// AI_GENERATE_END-------------
