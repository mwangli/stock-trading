// AI_GENERATE_START----------
package com.stock.application.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 持久化公共配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(
        basePackages = "com.stock",
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MongoRepository.class),
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JpaRepository.class))
public class MongoPersistenceConfig {
}
// AI_GENERATE_END----------
