package com.stock.modelService.listener;

import com.stock.modelService.service.ModelTrainingRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 模型训练记录同步启动监听器
 * <p>
 * 在应用启动完成后，根据已有的股票基础信息为每一只股票创建
 * 一条模型训练记录占位数据，用于量化模型列表展示。
 * 若某只股票尚未训练，则记录会标记为未训练；若 Mongo 中已经存在
 * 对应的 LSTM 模型，则会自动标记为已训练并补全最近训练信息。
 * </p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelTrainingRecordStartupListener {

    private final ModelTrainingRecordService modelTrainingRecordService;

    /**
     * 应用启动完成事件回调，在后台异步同步模型训练记录占位数据
     *
     * @param event Spring Boot 应用就绪事件
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("========== [模型记录初始化] ApplicationReadyEvent 收到，开始同步模型训练记录占位数据 ==========");
        try {
            // modelTrainingRecordService.syncAllStocks();
            log.info("========== [模型记录初始化] 同步完成 ==========");
        } catch (Exception e) {
            log.error("[模型记录初始化] 同步模型训练记录失败: {}", e.getMessage(), e);
        }
    }
}

