package com.stock.databus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@MapperScan("com.stock.databus.repository")
@EnableMongoRepositories("com.stock.databus.repository")
public class StockDatabusApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockDatabusApplication.class, args);
    }
}
