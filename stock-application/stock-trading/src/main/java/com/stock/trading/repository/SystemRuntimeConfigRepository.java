// AI_GENERATE_START-------------
package com.stock.trading.repository;

import com.stock.trading.entity.SystemRuntimeConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
/**
 * 系统运行控制配置仓储。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface SystemRuntimeConfigRepository extends MongoRepository<SystemRuntimeConfig, String> {
}
// AI_GENERATE_END-------------
