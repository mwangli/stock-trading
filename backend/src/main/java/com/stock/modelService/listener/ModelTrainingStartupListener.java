package com.stock.modelService.listener;

import com.stock.dataCollector.persistence.MonthlyPriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.persistence.WeeklyPriceRepository;
import com.stock.dataCollector.service.StockDataService;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 模型服务启动监听器
 * 负责在应用启动后：
 * 1. 检查并聚合K线数据（周K、月K）
 * 2. 检查并训练缺失的 LSTM 模型
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.startup.listener.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ModelTrainingStartupListener {

    private final StockInfoRepository stockInfoRepository;
    private final WeeklyPriceRepository weeklyPriceRepository;
    private final MonthlyPriceRepository monthlyPriceRepository;
    private final StockDataService stockDataService;
    private final LstmModelRepository lstmModelRepository;
    private final LstmTrainerService lstmTrainerService;

    /** 是否在启动时执行模型检查与训练，由 app.startup.model-training.enabled 控制 */
    @Value("${app.startup.model-training.enabled:true}")
    private boolean modelTrainingEnabled;

    /** 训练线程数，0 表示使用 CPU 核数 */
    @Value("${app.startup.model-training.threads:0}")
    private int modelTrainingThreads;

    /**
     * 监听应用启动完成事件
     * 使用 @Async 在独立线程中执行，不阻塞启动；内部再异步执行 K 线聚合与模型训练
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========== 应用启动监听器触发 ==========");

        // 使用 CompletableFuture 异步执行，确保不阻塞 Spring Boot 启动过程
        CompletableFuture.runAsync(this::aggregateKLineDataIfNeeded);
        if (modelTrainingEnabled) {
            CompletableFuture.runAsync(this::checkAndTrainModels);
        } else {
            log.info("模型训练开关已关闭 (app.startup.model-training.enabled=false)，跳过启动时模型训练");
        }
    }

    /**
     * 检查并聚合K线数据
     * 如果周K或月K数据为空，则执行聚合
     */
    private void aggregateKLineDataIfNeeded() {
        try {
            log.info("========== 开始检查K线数据聚合状态 ==========");

            long weeklyCount = weeklyPriceRepository.count();
            long monthlyCount = monthlyPriceRepository.count();

            log.info("当前周K数据量: {}", weeklyCount);
            log.info("当前月K数据量: {}", monthlyCount);

            if (weeklyCount == 0 || monthlyCount == 0) {
                log.info("检测到K线数据为空，开始执行聚合操作...");

                long startTime = System.currentTimeMillis();
                StockDataService.AggregateResult result = stockDataService.aggregateAllKLineData();
                long costTime = System.currentTimeMillis() - startTime;

                if (result != null) {
                    log.info("========== K线数据聚合完成 ==========");
                    log.info("聚合结果 - 周K: {} 条, 月K: {} 条, 耗时: {} ms",
                            result.getWeeklyCount(), result.getMonthlyCount(), costTime);
                } else {
                    log.warn("K线数据聚合返回结果为空");
                }
            } else {
                log.info("K线数据已存在，跳过聚合操作");
                log.info("========== K线数据检查完成 ==========");
            }

        } catch (Exception e) {
            logStartupTaskException("K线数据聚合", e);
        }
    }

    /**
     * 检查并训练模型的核心逻辑
     * 使用线程池按 CPU 核数并行训练，训练分数（trainLoss/valLoss）会随模型保存至 MongoDB
     */
    private void checkAndTrainModels() {
        try {
            if (!modelTrainingEnabled) {
                return;
            }
            log.info("开始检查并训练缺失的 LSTM 模型...");

            // 1. 获取所有股票代码
            List<String> stockCodes = stockInfoRepository.findAllCodes();

            if (stockCodes.isEmpty()) {
                log.warn("未发现任何股票代码，请检查数据采集模块是否已同步基础数据");
                return;
            }

            log.info("发现共 {} 只股票需要检查", stockCodes.size());

            // 2. 批量查询已存在的模型，使用线程安全 Set 供多线程写入
            long batchQueryStart = System.nanoTime();
            Set<String> existingModels = ConcurrentHashMap.newKeySet();
            existingModels.addAll(lstmModelRepository.findByModelNameIn(stockCodes));
            long batchQueryMs = (System.nanoTime() - batchQueryStart) / 1_000_000;
            log.info("批量查询已存在模型完成，耗时 {} ms，共 {} 个模型已存在", batchQueryMs, existingModels.size());

            // 3. 筛选出需要训练的股票代码
            List<String> codesToTrain = stockCodes.stream()
                    .filter(code -> !existingModels.contains(code))
                    .collect(Collectors.toList());

            int skippedCount = stockCodes.size() - codesToTrain.size();
            if (codesToTrain.isEmpty()) {
                log.info("========== 模型检查与训练任务完成（无待训练模型）==========");
                log.info("总扫描: {}，已存在(跳过): {}", stockCodes.size(), skippedCount);
                return;
            }

            // 4. 根据配置或 CPU 核数确定训练线程数
            int nThreads = modelTrainingThreads > 0
                    ? modelTrainingThreads
                    : Runtime.getRuntime().availableProcessors();
            nThreads = Math.min(nThreads, codesToTrain.size());
            log.info("使用 {} 个线程并行训练，待训练 {} 只股票", nThreads, codesToTrain.size());

            AtomicInteger trainedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            long startNs = System.nanoTime();

            ExecutorService executor = Executors.newFixedThreadPool(nThreads);
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String code : codesToTrain) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            log.info("股票 {} 开始训练...", code);
                            long oneStart = System.nanoTime();
                            LstmTrainerService.TrainingResult result = lstmTrainerService.trainModel(code, 500, null, null, null);
                            long oneMs = (System.nanoTime() - oneStart) / 1_000_000;

                            if (result.isSuccess()) {
                                trainedCount.incrementAndGet();
                                existingModels.add(code);
                                log.info("股票 {} 训练完成，耗时 {} 秒，trainLoss={}, valLoss={}（已保存至 MongoDB）",
                                        code,
                                        String.format("%.2f", oneMs / 1000.0),
                                        result.getTrainLoss(),
                                        result.getValLoss());
                            } else {
                                failedCount.incrementAndGet();
                                log.warn("股票 {} 训练未成功: {}", code, result.getMessage());
                            }
                        } catch (Exception e) {
                            failedCount.incrementAndGet();
                            log.error("股票 {} 训练失败: {}", code, e.getMessage());
                        }
                    }, executor);
                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }

            long durationSeconds = (System.nanoTime() - startNs) / 1_000_000_000;
            log.info("========== 模型检查与训练任务完成 ==========");
            log.info("耗时: {} 秒，线程数: {}", durationSeconds, nThreads);
            log.info("总扫描: {}", stockCodes.size());
            log.info("新训练: {}（训练分数 trainLoss/valLoss 已保存至 MongoDB）", trainedCount.get());
            log.info("已存在(跳过): {}", skippedCount);
            log.info("失败: {}", failedCount.get());

        } catch (Exception e) {
            logStartupTaskException("模型检查与训练", e);
        }
    }

    /**
     * 统一输出启动任务异常：单行摘要 + 可选的配置类提示，完整堆栈仅 DEBUG 输出
     */
    private void logStartupTaskException(String taskName, Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String hint = buildExceptionHint(e);
        log.error("[启动任务] {} 失败: {} {}", taskName, msg, hint);
        if (log.isDebugEnabled()) {
            log.debug("[启动任务] {} 完整堆栈", taskName, e);
        }
    }

    private String buildExceptionHint(Exception e) {
        Throwable t = e;
        while (t != null) {
            String name = t.getClass().getName();
            String lower = name.toLowerCase();
            if (lower.contains("configurationpropertiesbind") || lower.contains("bind")) {
                return "| 建议: 检查 application.yml 中相关配置项格式与缩进";
            }
            if (lower.contains("redis")) {
                return "| 建议: 检查 spring.data.redis 配置 (host/port/password/database)";
            }
            if (lower.contains("datasource") || lower.contains("jdbc")) {
                return "| 建议: 检查数据源配置与数据库可达性";
            }
            t = t.getCause();
        }
        return "";
    }
}
