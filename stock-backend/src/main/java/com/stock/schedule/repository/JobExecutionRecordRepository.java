// AI_GENERATE_START-----------
package com.stock.schedule.repository;

import com.stock.schedule.entity.JobExecutionRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 任务执行记录仓库。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Repository
public interface JobExecutionRecordRepository
        extends JpaRepository<JobExecutionRecord, Long>, JpaSpecificationExecutor<JobExecutionRecord> {

    /**
     * 查询运行标识对应的执行记录。
     *
     * @param runId 运行标识
     * @return 执行记录
     */
    Optional<JobExecutionRecord> findByRunId(String runId);

    /**
     * 查询指定任务最近一次执行记录。
     *
     * @param jobName 任务名称
     * @return 最近一次记录
     */
    Optional<JobExecutionRecord> findTopByJobNameOrderByCreatedAtDesc(String jobName);

}
// AI_GENERATE_END-----------
