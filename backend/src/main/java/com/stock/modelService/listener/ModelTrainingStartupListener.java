package com.stock.modelService.listener;

import com.stock.dataCollector.repository.StockInfoRepository;
import com.stock.modelService.repository.LstmModelRepository;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型服务启动监听器
 * 负责在应用启动后检查并训练缺失的 LSTM 模型
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.startup.listener.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ModelTrainingStartupListener {

    private final StockInfoRepository stockInfoRepository;
    private final LstmModelRepository lstmModelRepository;
    private final LstmTrainerService lstmTrainerService;

    /**
     * 监听应用启动完成事件
     * 异步执行模型检查和训练任务，避免阻塞主线程
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========== 模型服务启动监听器触发 ==========");
        
        // 使用 CompletableFuture 异步执行，确保不阻塞 Spring Boot 启动过程
        CompletableFuture.runAsync(this::checkAndTrainModels);
    }

    /**
     * 检查并训练模型的核心逻辑
     */
    private void checkAndTrainModels() {
        try {
            log.info("开始检查并训练缺失的 LSTM 模型...");
            
            // 1. 获取所有股票代码
            // 注意：这里可能会获取到几千只股票，后续可能需要分页处理或限制数量
            List<String> stockCodes = stockInfoRepository.findAllCodes();
            
            if (stockCodes.isEmpty()) {
                log.warn("未发现任何股票代码，请检查数据采集模块是否已同步基础数据");
                return;
            }
            
            log.info("发现共 {} 只股票需要检查", stockCodes.size());
            
            AtomicInteger trainedCount = new AtomicInteger(0);
            AtomicInteger skippedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            
            long startNs = System.nanoTime();
            
            // 2. 遍历检查每只股票
            for (int i = 0; i < stockCodes.size(); i++) {
                String code = stockCodes.get(i);
                
                try {
                    // 检查模型是否存在 (利用之前添加的 existsByModelName 方法)
                    // 模型名称约定与股票代码一致
                    if (lstmModelRepository.existsByModelName(code)) {
                        skippedCount.incrementAndGet();
                        if (skippedCount.get() % 100 == 0) {
                            int done = trainedCount.get() + skippedCount.get() + failedCount.get();
                            String percentText = String.format("%.2f", done * 100.0 / stockCodes.size());
                            log.info(
                                    "总体进度: {}/{} ({}%) - 已跳过 {} 个已存在的模型",
                                    done,
                                    stockCodes.size(),
                                    percentText,
                                    skippedCount.get()
                            );
                        }
                        continue;
                    }

                    log.info("股票 {} 模型不存在，开始训练 ({}/{})...", code, i + 1, stockCodes.size());

                    // 3. 训练模型
                    // days=500: 使用最近500个交易日的数据
                    // epochs=null, batchSize=null, learningRate=null: 使用配置文件中的默认值
                    long oneTrainStartNs = System.nanoTime();
                    LstmTrainerService.TrainingResult result = lstmTrainerService.trainModel(code, 500, null, null, null);
                    long oneTrainMs = (System.nanoTime() - oneTrainStartNs) / 1_000_000;
                    String oneTrainSecondsText = String.format("%.2f", oneTrainMs / 1000.0);

                    if (result.isSuccess()) {
                        trainedCount.incrementAndGet();
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
                    
                    
                    // 简单的流控，避免瞬间占用过多 CPU/内存
                    // 实际生产环境可能需要更复杂的任务队列机制
                    Thread.sleep(500); 
                    
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
                    // 继续处理下一个，不中断循环
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
