package com.stock.modelService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.client.RestTemplate;

/**
 * 模型服务模块独立启动类
 * 
 * 启动命令: mvn spring-boot:run -pl model-service
 * 端口: 8082
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.stock.modelService",
    "com.stock.dataCollector"
})
@EntityScan(basePackages = {
    "com.stock.dataCollector.entity"
})
@EnableMongoRepositories(basePackages = {
    "com.stock.dataCollector.repository"
})
@EnableJpaRepositories(basePackages = {
    "com.stock.dataCollector.repository.mysql"
})
public class ModelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}