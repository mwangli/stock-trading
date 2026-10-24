// AI_GENERATE_START---
package com.stock.platform.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 查询缓存配置。
 * 缓存仅作为可丢失加速层，读写异常会退回权威存储，不参与交易和任务状态判定。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private final RedisConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Redis 缓存配置。
     *
     * @param connectionFactory Redis 连接工厂
     * @param objectMapper 应用 JSON 映射器
     */
    public RedisCacheConfig(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建按业务数据类型设置过期时间的 Redis CacheManager。
     *
     * @return Redis 缓存管理器
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaults = baseConfiguration(Duration.ofMinutes(10));
        Map<String, RedisCacheConfiguration> configurations = Map.of(
                CacheNames.STOCK_INFO, baseConfiguration(Duration.ofMinutes(10)),
                CacheNames.LATEST_PRICE, baseConfiguration(Duration.ofSeconds(30)),
                CacheNames.TRADING_CALENDAR, baseConfiguration(Duration.ofHours(6)),
                CacheNames.ACTIVE_MODEL, baseConfiguration(Duration.ofMinutes(5)),
                CacheNames.RUNTIME_CONFIG, baseConfiguration(Duration.ofMinutes(1)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(configurations)
                .build();
    }

    /**
     * 创建缓存异常降级处理器，避免 Redis 缓存故障改变业务处理结果。
     *
     * @return 缓存异常处理器
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private RedisCacheConfiguration baseConfiguration(Duration ttl) {
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.stock.")
                        .allowIfSubType("java.")
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));
    }

    private static final class LoggingCacheErrorHandler implements CacheErrorHandler {

        private static final Logger LOG = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        /**
         * 记录缓存读取失败并允许业务方法继续查询权威存储。
         *
         * @param exception Redis 异常
         * @param cache 缓存实例
         * @param key 缓存键
         */
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            LOG.warn("Redis 缓存读取失败，已回退权威存储: cache={}, key={}", cache.getName(), key, exception);
        }

        /**
         * 记录缓存写入失败，不改变业务方法返回结果。
         *
         * @param exception Redis 异常
         * @param cache 缓存实例
         * @param key 缓存键
         * @param value 待缓存值
         */
        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            LOG.warn("Redis 缓存写入失败: cache={}, key={}", cache.getName(), key, exception);
        }

        /**
         * 记录单键缓存失效失败，权威存储写入结果保持不变。
         *
         * @param exception Redis 异常
         * @param cache 缓存实例
         * @param key 缓存键
         */
        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            LOG.warn("Redis 缓存失效失败: cache={}, key={}", cache.getName(), key, exception);
        }

        /**
         * 记录缓存清空失败，权威存储写入结果保持不变。
         *
         * @param exception Redis 异常
         * @param cache 缓存实例
         */
        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            LOG.warn("Redis 缓存清空失败: cache={}", cache.getName(), exception);
        }
    }
}
// AI_GENERATE_END---
