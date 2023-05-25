package online.mwang.foundtrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class FoundTradingApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.SHORT_IDS.get("CTT")));
        SpringApplication.run(FoundTradingApplication.class, args);
    }

}
