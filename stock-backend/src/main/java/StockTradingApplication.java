package online.mwang.stockTrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 股票交易系统启动类
 */
@EnableRetry
@EnableScheduling
@SpringBootApplication
public class StockTradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockTradingApplication.class, args);
    }
}
