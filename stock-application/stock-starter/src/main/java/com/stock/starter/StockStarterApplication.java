// AI_GENERATE_START-------
package com.stock.starter;

import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 股票交易系统主应用启动类。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@SpringBootApplication(scanBasePackages = "com.stock")
public class StockStarterApplication {

    /** 进程启动时间戳，用于计算启动耗时。
     * -- GETTER --
     *  获取应用启动时间戳。
     *
     * @return 启动时间戳，单位毫秒
     */
    @Getter
    private static long startTimeMs;

    /**
     * 启动股票交易系统。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        startTimeMs = System.currentTimeMillis();
        SpringApplication.run(StockStarterApplication.class, args);
        System.out.println("========================================");
        System.out.println("  AI 股票交易系统启动成功!");
        System.out.println("  启动模块: stock-starter");
        System.out.println("  编排模块: stock-job");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("========================================");
    }
}
// AI_GENERATE_END-------
