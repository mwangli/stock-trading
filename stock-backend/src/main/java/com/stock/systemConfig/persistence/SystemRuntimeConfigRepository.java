// AI_GENERATE_START ---
package com.stock.systemConfig.persistence;

import com.stock.systemConfig.domain.entity.SystemRuntimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统运行控制配置仓储。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface SystemRuntimeConfigRepository extends JpaRepository<SystemRuntimeConfig, String> {

    /**
     * 在节点配置不存在时原子写入默认值。
     * 并发初始化同一节点时由数据库主键约束合并为同一条记录。
     *
     * @param config 待初始化的节点配置
     * @return 受影响的记录数
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO system_runtime_config (
                node_id,
                scheduler_enabled,
                data_collection_enabled,
                news_collection_enabled,
                startup_data_sync_enabled,
                startup_history_sync_enabled,
                startup_news_sync_enabled,
                model_training_enabled,
                updated_at
            ) VALUES (
                :#{#config.nodeId},
                :#{#config.schedulerEnabled},
                :#{#config.dataCollectionEnabled},
                :#{#config.newsCollectionEnabled},
                :#{#config.startupDataSyncEnabled},
                :#{#config.startupHistorySyncEnabled},
                :#{#config.startupNewsSyncEnabled},
                :#{#config.modelTrainingEnabled},
                :#{#config.updatedAt}
            )
            ON DUPLICATE KEY UPDATE node_id = node_id
            """, nativeQuery = true)
    int insertIfAbsent(SystemRuntimeConfig config);
}
// AI_GENERATE_END ---
