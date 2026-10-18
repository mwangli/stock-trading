// AI_GENERATE_START- -
package com.stock.tradingExecutor;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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
     * 判断任务是否存在未结束执行。
     *
     * @param jobName 任务名称
     * @param statuses 未结束状态
     * @return true 表示存在未结束执行
     */
    boolean existsByJobNameAndStatusIn(String jobName, Collection<JobExecutionStatus> statuses);

    /**
     * 查询指定任务最近一次执行记录。
     *
     * @param jobName 任务名称
     * @return 最近一次记录
     */
    Optional<JobExecutionRecord> findTopByJobNameOrderByCreatedAtDesc(String jobName);

    /**
     * 一次查询多个任务的最近执行记录，避免任务列表逐条查询形成 N+1 查询。
     *
     * @param jobNames 任务名称集合
     * @return 每个任务的最近一条执行记录
     */
    @Query("select item from JobExecutionRecord item "
            + "where item.jobName in :jobNames "
            + "and not exists (select newer.id from JobExecutionRecord newer "
            + "where newer.jobName = item.jobName "
            + "and (newer.createdAt > item.createdAt "
            + "or (newer.createdAt = item.createdAt and newer.id > item.id)))")
    List<JobExecutionRecord> findLatestByJobNames(@Param("jobNames") Collection<String> jobNames);
}
// AI_GENERATE_END --
