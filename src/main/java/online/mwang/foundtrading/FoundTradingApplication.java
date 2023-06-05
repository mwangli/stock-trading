package online.mwang.foundtrading;

import online.mwang.foundtrading.logs.LogsAppender;
import online.mwang.foundtrading.logs.WebSocketServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

//@EnableScheduling
@SpringBootApplication
public class FoundTradingApplication {

    public static void main(String[] args) {
//        SpringApplication.run(FoundTradingApplication.class, args);

        SpringApplication springApplication = new SpringApplication(FoundTradingApplication.class);
        ConfigurableApplicationContext configurableApplicationContext = springApplication.run(args);
        WebSocketServer.setApplicationContext(configurableApplicationContext);


    }
}
