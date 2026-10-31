// AI_GENERATE_START -
package com.stock.console;

import com.stock.console.config.ConsoleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ConsoleProperties.class)
public class StockConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockConsoleApplication.class, args);
    }
}
// AI_GENERATE_END -
