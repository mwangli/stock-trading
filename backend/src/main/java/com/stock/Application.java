package com.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * 股票交易系统主应用启动类
 * 
 * 启动命令: mvn spring-boot:run
 * 端口: 8080
 */
@SpringBootApplication(
        exclude = {
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        }
)
@EnableScheduling
public class Application { 

    /** 进程启动时间戳，用于计算启动耗时 */
    private static long startTimeMs;

    public static long getStartTimeMs() {
        return startTimeMs;
    }

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

    /**
     * RestTemplate 配置
     * 用于 HTTP 请求到证券平台
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);  // 15秒连接超时
        factory.setReadTimeout(60000);     // 60秒读取超时
        // factory.setOutputStreaming(false); // Deprecated in Spring Boot 3.x
        return new RestTemplate(factory);
    }

    /**
     * RedisTemplate 配置
     * 用于缓存和状态管理
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用 StringRedisSerializer 作为 key 的序列化方式
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // 使用 GenericJackson2JsonRedisSerializer，并注册 Java 8 时间模块以支持 LocalDateTime 等
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
