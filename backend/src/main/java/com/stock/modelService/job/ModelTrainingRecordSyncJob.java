package com.stock.modelService.job;

import com.stock.modelService.service.ModelTrainingRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型训练记录同步任务执行入口
 * <p>
 * 说明：
 * <ul>
 *     <li>本类不直接使用 @Scheduled 注解；</li>
 *     <li>由统一调度中心 {@link com.stock.tradingExecutor.job.JobSchedulerService}
 *     通过 JobConfig 中配置的 beanName + methodName 进行反射调用；</li>
 *     <li>默认任务配置见 {@link com.stock.tradingExecutor.job.JobBootstrap} 中的 modelTrainingRecordSync 任务。</li>
 * </ul>
 * 任务本身是幂等的，可被安全地按需和定时多次触发。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelTrainingRecordSyncJob {

    private final ModelTrainingRecordService modelTrainingRecordService;

    /**
     * 执行一次全量模型训练记录同步
     * <p>
     * 该方法会：
     * <ul>
     *     <li>为所有股票生成缺失的训练记录占位行；</li>
     *     <li>根据 MongoDB 中最新的 LSTM 模型补齐训练状态与统计字段。</li>
     * </ul>
     */
    public void syncAllStocks() {
        log.info("[ModelTrainingRecordSyncJob] 开始执行模型训练记录全量同步");
        modelTrainingRecordService.syncAllStocks();
        log.info("[ModelTrainingRecordSyncJob] 模型训练记录全量同步完成");
    }
}

