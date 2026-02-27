package com.example.aishopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aishopping.entity.CollectionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集任务Mapper接口
 */
@Mapper
public interface CollectionTaskMapper extends BaseMapper<CollectionTask> {

    /**
     * 查询待执行的定时任务
     */
    @Select("SELECT * FROM collection_tasks WHERE task_type = 'SCHEDULED' " +
            "AND status = 'PENDING' AND is_enabled = TRUE " +
            "AND next_run_time <= #{now} ORDER BY next_run_time")
    List<CollectionTask> selectPendingScheduledTasks(@Param("now") LocalDateTime now);

    /**
     * 更新任务状态
     */
    @Update("UPDATE collection_tasks SET status = #{status}, updated_at = NOW() WHERE id = #{taskId}")
    int updateStatus(@Param("taskId") Long taskId, @Param("status") String status);

    /**
     * 根据状态查询任务
     */
    @Select("SELECT * FROM collection_tasks WHERE status = #{status} ORDER BY created_at DESC")
    List<CollectionTask> selectByStatus(@Param("status") String status);
}
