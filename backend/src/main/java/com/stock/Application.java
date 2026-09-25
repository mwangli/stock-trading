// AI_GENERATE_START -
package com.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 股票交易系统主应用启动类。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    /** 进程启动时间戳，用于计算启动耗时。 */
    private static long startTimeMs;

    /**
     * 获取应用启动时间戳。
     *
     * @return 启动时间戳，单位毫秒
     */
    public static long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * 启动股票交易系统。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        startTimeMs = System.currentTimeMillis();
        SpringApplication.run(Application.class, args);
        System.out.println("========================================");
        System.out.println("  AI 股票交易系统启动成功!");
        System.out.println("  聚合模块: data-collector, model-service,");
        System.out.println("           strategy-analysis, trading-executor");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("========================================");
    }
}
// AI_GENERATE_END -
