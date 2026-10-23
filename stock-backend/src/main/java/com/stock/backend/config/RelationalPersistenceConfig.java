// AI_GENERATE_START --
package com.stock.backend.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * MySQL 与 JPA 持久化公共配置。
 * 显式排除 MongoRepository，避免混合持久化时仓储工厂误认。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Configuration
@EnableJpaAuditing
@EntityScan(basePackages = "com.stock")
@EnableJpaRepositories(
        basePackages = "com.stock",
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JpaRepository.class),
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MongoRepository.class))
public class RelationalPersistenceConfig {
}
// AI_GENERATE_END --
