// AI_GENERATE_START--------------
package com.stock.trading.repository;

import com.stock.trading.entity.SystemRuntimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/**
 * 系统运行控制配置仓储。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface SystemRuntimeConfigRepository extends JpaRepository<SystemRuntimeConfig, String> {
}
// AI_GENERATE_END--------------
