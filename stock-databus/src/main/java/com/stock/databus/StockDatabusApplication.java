package com.stock.databus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories("com.stock.databus.repository")
@EnableMongoRepositories("com.stock.databus.repository")
public class StockDatabusApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockDatabusApplication.class, args);
    }
}
