package com.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 股票交易系统主应用启动类
 *
 * 启动命令: mvn spring-boot:run
 * 端口: 8080
 */
@SpringBootApplication
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
     * Redis连接工厂配置
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * RedisTemplate 配置
     * 用于缓存和状态管理
     * 注意：统一使用 StringRedisSerializer 避免 JSON 序列化导致的双引号问题
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
