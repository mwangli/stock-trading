package com.stock.modelService.listener;

import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.service.StockDataService;
import com.stock.modelService.config.LstmDataQualityConfig;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * LSTM 模型训练启动监听器
 * <p>
 * 在应用启动完成后，按需自动为尚未训练的股票批量训练 LSTM 模型，
 * 以保证策略分析模块在首次运行时就具备可用的价格预测模型。
 * </p>
 *
 * <p>该监听器通过配置项进行精细控制：</p>
 * <ul>
 *     <li>{@code app.startup.model-training.enabled}：是否启用启动时自动训练（默认 true）</li>
 *     <li>训练为串行执行，依赖 GPU 加速（pytorch-native-cu121）提升单次训练速度</li>
 * </ul>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.startup.model-training", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LstmModelTrainingStartupListener {

    private static final int DEFAULT_TRAIN_DAYS = 500;

    private final StockInfoRepository stockInfoRepository;
    private final LstmModelRepository lstmModelRepository;
    private final LstmTrainerService lstmTrainerService;
    private final StockDataService stockDataService;
    private final LstmDataQualityConfig lstmDataQualityConfig;

    /**
     * 监听应用启动完成事件，在后台异步检查并训练缺失的 LSTM 模型
     *
     * @param event Spring Boot 应用启动完成事件
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("========== [LSTM 启动训练] 应用已就绪，准备在后台检查并训练缺失模型 ==========");
        CompletableFuture.runAsync(this::checkAndTrainModelsAsync);
    }

    /**
     * 检查并批量训练缺失的 LSTM 模型
     * <p>
     * 逻辑步骤：
     * 1. 加载所有股票代码列表；
     * 2. 批量查询 MongoDB 中已存在的 LSTM 模型（modelName 即股票代码）；
     * 3. 计算待训练股票集合；
     * 4. 串行训练，依赖 GPU 加速（若可用）。
     * </p>
     */
    protected void checkAndTrainModelsAsync() {
        try {
            long startNs = System.nanoTime();
            log.info("========== [LSTM 启动训练] 开始检查并训练缺失的 LSTM 模型 ==========");

            // 1. 获取所有股票代码
            List<String> stockCodes = stockInfoRepository.findAllCodes();
            if (stockCodes == null || stockCodes.isEmpty()) {
                log.warn("[LSTM 启动训练] 未发现任何股票代码，请先完成基础数据同步");
                return;
            }
            log.info("[LSTM 启动训练] 共发现 {} 只股票需要检查是否已有 LSTM 模型", stockCodes.size());

            // 2. 使用 distinct 查询所有已存在的 modelName（走索引，单次往返，毫秒级）
            long queryStartNs = System.nanoTime();
            List<String> existingList = lstmModelRepository.findAllModelNamesDistinct();
            Set<String> existingModels = ConcurrentHashMap.newKeySet();
            existingModels.addAll(existingList);
            long queryMs = (System.nanoTime() - queryStartNs) / 1_000_000;
            String sample = existingList.isEmpty() ? "[]" : existingList.stream().limit(20).collect(Collectors.joining(", ")) + (existingList.size() > 20 ? ", ..." : "");
            log.info("[LSTM 启动训练] SELECT all code (distinct) 完成，耗时 {} ms，结果数量: {}，示例: {}", queryMs, existingModels.size(), sample);

            // 3. 计算待训练股票代码列表
            List<String> codesToTrain = stockCodes.stream()
                    .filter(code -> !existingModels.contains(code))
                    .collect(Collectors.toList());
            int skippedCount = stockCodes.size() - codesToTrain.size();
            log.info("[LSTM 启动训练] 计算待训练列表完成 -> 待训练模型数: {}, 已存在(跳过)模型数: {}", codesToTrain.size(), skippedCount);
            if (codesToTrain.isEmpty()) {
                log.info("========== [LSTM 启动训练] 无待训练模型，任务结束 ==========");
                log.info("[LSTM 启动训练] 总扫描: {}，已存在(跳过): {}", stockCodes.size(), skippedCount);
                return;
            }

            // 4. 串行训练：DJL/PyTorch 不支持多线程，依赖 GPU 加速单次训练
            log.info("[LSTM 启动训练] 串行训练（GPU 加速），待训练股票数量: {}", codesToTrain.size());

            AtomicInteger trainedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String code : codesToTrain) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            long oneStartNs = System.nanoTime();
                            log.info("[LSTM 启动训练] 股票 {} 开始训练...", code);

                            // 如果股票在数据质量黑名单中，直接跳过训练，避免死循环
                            if (lstmDataQualityConfig.getSkipTrainingCodes().contains(code)) {
                                log.warn("[LSTM 启动训练] 股票 {} 位于数据质量黑名单列表，跳过训练", code);
                                return;
                            }

                            LstmTrainerService.TrainingResult result =
                                    lstmTrainerService.trainModel(code, DEFAULT_TRAIN_DAYS, null, null, null, "full");

                            long oneMs = (System.nanoTime() - oneStartNs) / 1_000_000;
                            if (result != null && result.isSuccess()) {
                                trainedCount.incrementAndGet();
                                existingModels.add(code);
                                log.info("[LSTM 启动训练] 股票 {} 训练完成，耗时 {} 秒，trainLoss={}, valLoss={}",
                                        code,
                                        String.format("%.2f", oneMs / 1000.0),
                                        result.getTrainLoss(),
                                        result.getValLoss());
                            } else if (result != null) {
                                failedCount.incrementAndGet();
                                String message = result.getMessage();
                                log.warn("[LSTM 启动训练] 股票 {} 训练未成功: {}", code, message);

                                // 当历史数据不足以支撑 LSTM 训练时，主动清理本地历史数据，
                                // 让后续的历史数据同步任务重新写入一份完整数据。
                                if (message != null && message.contains("数据预处理失败")) {
                                    log.warn("[LSTM 启动训练] 股票 {} 因历史数据不足，将清理本地历史行情数据，等待下次历史同步重建", code);
                                    try {
                                        stockDataService.clearHistoryForStock(code);
                                    } catch (Exception ex) {
                                        log.error("[LSTM 启动训练] 股票 {} 清理历史数据失败: {}", code, ex.getMessage(), ex);
                                    }
                                }
                            } else {
                                failedCount.incrementAndGet();
                                log.warn("[LSTM 启动训练] 股票 {} 训练返回结果为空", code);
                            }
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            log.error("[LSTM 启动训练] 股票 {} 训练失败: {}", code, e.getMessage(), e);
                        }
                    }, executor);
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }

            long durationSeconds = (System.nanoTime() - startNs) / 1_000_000_000;
            log.info("========== [LSTM 启动训练] 任务完成 ==========");
            log.info("[LSTM 启动训练] 总耗时: {} 秒", durationSeconds);
            log.info("[LSTM 启动训练] 总扫描股票数: {}", stockCodes.size());
            log.info("[LSTM 启动训练] 新训练模型数: {}", trainedCount.get());
            log.info("[LSTM 启动训练] 已存在(跳过)模型数: {}", skippedCount);
            log.info("[LSTM 启动训练] 训练失败模型数: {}", failedCount.get());

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[LSTM 启动训练] 执行失败: {} | 建议: 检查 MongoDB/历史行情数据是否就绪", msg, e);
        }
    }
}
