// AI_GENERATE_START------
package com.stock.job.repository;

import com.stock.job.entity.JobConfig;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 固定任务配置 MongoDB 仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface JobConfigRepository extends MongoRepository<JobConfig, Long> {

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
    void deleteByJobName(String jobName);
}
// AI_GENERATE_END------
