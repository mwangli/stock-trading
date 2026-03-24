package com.stock.dataCollector.support;

import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 应用启动时新闻采集监听器
 * 启动后遍历所有股票，先采集新闻列表（标题），再获取详情并写入数据库
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.startup.news-sync.enabled", havingValue = "true")
public class NewsStartupListener {

    private final StockNewsService stockNewsService;
    private final StockInfoRepository stockInfoRepository;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        CompletableFuture.runAsync(() -> {
            log.info("========== [启动监听] 新闻采集任务开始 ==========");
            try {
                List<String> codes = stockInfoRepository.findAllCodes();
                if (codes == null || codes.isEmpty()) {
                    log.warn("[启动监听] 未找到任何股票代码，跳过新闻采集");
                    return;
                }
                log.info("[启动监听] 待采集新闻的股票总数: {}", codes.size());

                StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(0);

                log.info("========== [启动监听] 新闻采集完成: 处理 {} 只股票，新增 {} 条新闻，失败 {} 只 ==========",
                        result.processedCount(), result.savedCount(), result.failedCount());
            } catch (Exception e) {
                log.error("[启动监听] 新闻采集任务执行失败", e);
            }
        });
    }
}
