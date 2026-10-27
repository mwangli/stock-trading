// AI_GENERATE_START----------
package com.stock.platform.repository;

import com.stock.platform.entity.JobConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 固定任务配置 MySQL 仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface JobConfigRepository extends JpaRepository<JobConfig, Long> {

    /**
     * 按固定任务名称查询配置。
     *
     * @param jobName 任务名称
     * @return 匹配的任务配置，不存在时为空
     */
    Optional<JobConfig> findByJobName(String jobName);

    /**
     * 删除废弃的固定任务配置。
     *
     * @param jobName 任务名称
     */
    @Transactional
    void deleteByJobName(String jobName);
}
// AI_GENERATE_END----------
