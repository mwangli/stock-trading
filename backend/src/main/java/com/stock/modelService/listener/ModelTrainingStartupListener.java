package com.stock.modelService.listener;

import com.stock.dataCollector.repository.MonthlyPriceRepository;
import com.stock.dataCollector.repository.StockInfoRepository;
import com.stock.dataCollector.repository.WeeklyPriceRepository;
import com.stock.dataCollector.service.StockDataService;
import com.stock.modelService.repository.LstmModelRepository;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 监听应用启动完成事件
     * 异步执行K线聚合和模型训练任务，避免阻塞主线程
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========== 应用启动监听器触发 ==========");

        // 使用 CompletableFuture 异步执行，确保不阻塞 Spring Boot 启动过程
        CompletableFuture.runAsync(this::aggregateKLineDataIfNeeded);
        CompletableFuture.runAsync(this::checkAndTrainModels);
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
            log.error("K线数据聚合任务发生异常", e);
        }
    }

    /**
     * 检查并训练模型的核心逻辑
     */
    private void checkAndTrainModels() {
        try {
            log.info("开始检查并训练缺失的 LSTM 模型...");

            // 1. 获取所有股票代码
            List<String> stockCodes = stockInfoRepository.findAllCodes();

            if (stockCodes.isEmpty()) {
                log.warn("未发现任何股票代码，请检查数据采集模块是否已同步基础数据");
                return;
            }

            log.info("发现共 {} 只股票需要检查", stockCodes.size());

            // 2. 批量查询已存在的模型，避免 N+1 查询问题
            long batchQueryStart = System.nanoTime();
            Set<String> existingModels = new HashSet<>(
                lstmModelRepository.findByModelNameIn(stockCodes)
            );
            long batchQueryMs = (System.nanoTime() - batchQueryStart) / 1_000_000;
            log.info("批量查询已存在模型完成，耗时 {} ms，共 {} 个模型已存在", batchQueryMs, existingModels.size());

            AtomicInteger trainedCount = new AtomicInteger(0);
            AtomicInteger skippedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            long startNs = System.nanoTime();

            // 3. 遍历检查每只股票
            for (int i = 0; i < stockCodes.size(); i++) {
                String code = stockCodes.get(i);

                try {
                    // 使用内存 Set 检查，避免重复数据库查询
                    if (existingModels.contains(code)) {
                        skippedCount.incrementAndGet();
                        if (skippedCount.get() % 500 == 0) {
                            int done = trainedCount.get() + skippedCount.get() + failedCount.get();
                            String percentText = String.format("%.2f", done * 100.0 / stockCodes.size());
                            log.info(
                                    "总体进度: {}/{} ({}%) - 详情: [已跳过: {}, 新训练: {}, 失败: {}]",
                                    done,
                                    stockCodes.size(),
                                    percentText,
                                    skippedCount.get(),
                                    trainedCount.get(),
                                    failedCount.get()
                            );
                        }
                        continue;
                    }

                    log.info("股票 {} 模型不存在，开始训练 ({}/{})...", code, i + 1, stockCodes.size());

                    // 4. 训练模型
                    long oneTrainStartNs = System.nanoTime();
                    LstmTrainerService.TrainingResult result = lstmTrainerService.trainModel(code, 500, null, null, null);
                    long oneTrainMs = (System.nanoTime() - oneTrainStartNs) / 1_000_000;
                    String oneTrainSecondsText = String.format("%.2f", oneTrainMs / 1000.0);

                    if (result.isSuccess()) {
                        trainedCount.incrementAndGet();
                        // 将新训练的模型添加到 Set 中，避免重复查询
                        existingModels.add(code);
                        int done = trainedCount.get() + skippedCount.get() + failedCount.get();
                        String percentText = String.format("%.2f", done * 100.0 / stockCodes.size());
                        log.info(
                                "股票 {} 模型训练完成，耗时 {} 秒；总体进度: {}/{} ({}%)",
                                code,
                                oneTrainSecondsText,
                                done,
                                stockCodes.size(),
                                percentText
                        );
                    } else {
                        failedCount.incrementAndGet();
                        int done = trainedCount.get() + skippedCount.get() + failedCount.get();
                        String percentText = String.format("%.2f", done * 100.0 / stockCodes.size());
                        log.warn(
                                "股票 {} 模型训练未成功，耗时 {} 秒；原因: {}；总体进度: {}/{} ({}%)",
                                code,
                                oneTrainSecondsText,
                                result.getMessage(),
                                done,
                                stockCodes.size(),
                                percentText
                        );
                    }

                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    int done = trainedCount.get() + skippedCount.get() + failedCount.get();
                    String percentText = String.format("%.2f", done * 100.0 / stockCodes.size());
                    log.error(
                            "股票 {} 模型训练失败: {}；总体进度: {}/{} ({}%)",
                            code,
                            e.getMessage(),
                            done,
                            stockCodes.size(),
                            percentText
                    );
                }
            }

            long durationSeconds = (System.nanoTime() - startNs) / 1_000_000_000;

            log.info("========== 模型检查与训练任务完成 ==========");
            log.info("耗时: {} 秒", durationSeconds);
            log.info("总扫描: {}", stockCodes.size());
            log.info("新训练: {}", trainedCount.get());
            log.info("已存在(跳过): {}", skippedCount.get());
            log.info("失败: {}", failedCount.get());

        } catch (Exception e) {
            log.error("模型启动检查任务发生未捕获异常", e);
        }
    }
}
