package com.stock.modelService.listener;

import com.stock.modelService.service.ModelTrainingRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 模型训练记录同步启动监听器
 * <p>
 * 在应用启动完成后触发模型训练记录初始化与状态同步流程：
 * <ul>
 *     <li>若模型训练记录表为空，则基于 StockInfo 表为每一只股票创建占位记录；</li>
 *     <li>随后根据 Mongo 中已有的 LSTM 模型，同步每只股票的训练状态与最近训练信息。</li>
 * </ul>
 * 该流程确保前端“量化模型”页面在首次部署后即可看到完整的股票列表及训练状态。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.startup.model-training.enabled", havingValue = "true", matchIfMissing = false)
public class ModelTrainingRecordStartupListener {

    private final ModelTrainingRecordService modelTrainingRecordService;

    /**
     * 应用启动完成事件回调，在后台异步执行初始化与同步流程
     *
     * @param event Spring Boot 应用就绪事件
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("========== [模型记录初始化] ApplicationReadyEvent 收到，开始初始化与同步模型训练记录 ==========");
        try {
            modelTrainingRecordService.initRecordsFromStockInfoIfEmpty();
            modelTrainingRecordService.syncAllStocks();
            log.info("========== [模型记录初始化] 初始化与同步流程完成 ==========");
        } catch (Exception e) {
            log.error("========== [模型记录初始化] 同步模型训练记录失败: {} ==========", e.getMessage(), e);
        }
    }
}

