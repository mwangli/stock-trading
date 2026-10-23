// AI_GENERATE_START------
package com.stock.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB 持久化公共配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.stock")
public class MongoPersistenceConfig {
}
// AI_GENERATE_END------
