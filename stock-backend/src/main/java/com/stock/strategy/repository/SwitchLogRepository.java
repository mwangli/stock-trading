// AI_GENERATE_START------
package com.stock.strategy.repository;

import com.stock.strategy.entity.SwitchLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略切换日志Repository
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface SwitchLogRepository extends MongoRepository<SwitchLog, String> {

    /**
     * 查询最近的切换日志
     */
    List<SwitchLog> findTop10ByOrderBySwitchTimeDesc();

    /**
     * 根据日期范围查询切换日志
     */
    List<SwitchLog> findBySwitchTimeBetween(LocalDate startTime, LocalDate endTime);
}
// AI_GENERATE_END------
