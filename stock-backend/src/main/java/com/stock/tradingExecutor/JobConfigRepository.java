// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobConfigRepository extends JpaRepository<JobConfig, Long> {

    Optional<JobConfig> findByJobName(String jobName);

    /**
     * 删除废弃的固定任务配置。
     *
     * @param jobName 任务名称
     */
    void deleteByJobName(String jobName);
}
// AI_GENERATE_END -
