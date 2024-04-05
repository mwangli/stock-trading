package online.mwang.stockTrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author 13255
 */
@EnableScheduling
@SpringBootApplication
public class FoundTradingApplication {

    public static void main(String[] args) {

        System.setProperty("java.awt.headless", "false");


//        SpringApplication.run(FoundTradingApplication.class, args);

        SpringApplicationBuilder builder = new SpringApplicationBuilder(FoundTradingApplication.class);
        builder.headless(false).run(args);

    }
}
