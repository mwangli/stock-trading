package com.stock.dataCollector.scheduled;

import com.stock.dataCollector.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 股票新闻采集定时任务
 * TODO: 待实现新闻采集功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockNewsScheduler {

    private final StockNewsService stockNewsService;

    /**
     * 定时采集股票新闻
     * 每小时执行一次
     * TODO: 实现后启用
     */
    @Scheduled(cron = "0 0 * * * *")
    public void collectHourlyNews() {
        log.info("股票新闻采集功能待实现，跳过执行");
        // TODO: 实现新闻采集后取消注释
        // stockNewsService.collectStockNews();
    }

    /**
     * 采集所有股票的最新新闻
     * 每日上午 9:00 执行
     * TODO: 实现后启用
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void collectDailyNews() {
        log.info("股票新闻批量采集功能待实现，跳过执行");
        // TODO: 实现新闻采集后取消注释
        // stockNewsService.collectAllStockNews();
    }
}