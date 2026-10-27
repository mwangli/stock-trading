// AI_GENERATE_START------
package com.stock.application.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.stock.base.cache.CacheNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 单 JVM 查询缓存配置。
 * 缓存仅作为可丢失加速层，进程重启后自动重建，不参与交易、任务或模型状态判定。
 *
 * @author mwangli
 * @since 2026-10-26
 */
@Configuration
@EnableCaching
public class InMemoryCacheConfig implements CachingConfigurer {

    /**
     * 创建按业务数据变化频率设置容量和过期时间的 Caffeine CacheManager。
     *
     * @return 进程内缓存管理器
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                cache(CacheNames.STOCK_INFO, 5_000, Duration.ofMinutes(10)),
                cache(CacheNames.LATEST_PRICE, 10_000, Duration.ofSeconds(30)),
                cache(CacheNames.TRADING_CALENDAR, 1_000, Duration.ofHours(6)),
                cache(CacheNames.ACTIVE_MODEL, 100, Duration.ofMinutes(5)),
                cache(CacheNames.RUNTIME_CONFIG, 100, Duration.ofMinutes(1))));
        return cacheManager;
    }

    /**
     * 创建缓存异常降级处理器，缓存故障时继续访问权威存储。
     *
     * @return 缓存异常处理器
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private CaffeineCache cache(String name, long maximumSize, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }

    private static final class LoggingCacheErrorHandler implements CacheErrorHandler {

        private static final Logger LOG = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        /** {@inheritDoc} */
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            LOG.warn("进程内缓存读取失败，已回退权威存储: cache={}, key={}", cache.getName(), key, exception);
        }

        /** {@inheritDoc} */
        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            LOG.warn("进程内缓存写入失败: cache={}, key={}", cache.getName(), key, exception);
        }

        /** {@inheritDoc} */
        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            LOG.warn("进程内缓存失效失败: cache={}, key={}", cache.getName(), key, exception);
        }

        /** {@inheritDoc} */
        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            LOG.warn("进程内缓存清空失败: cache={}", cache.getName(), exception);
        }
    }
}
// AI_GENERATE_END------
