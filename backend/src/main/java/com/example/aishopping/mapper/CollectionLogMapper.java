package com.example.aishopping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aishopping.entity.CollectionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采集日志Mapper接口
 */
@Mapper
public interface CollectionLogMapper extends BaseMapper<CollectionLog> {

    /**
     * 根据任务ID查询日志
     */
    @Select("SELECT * FROM collection_logs WHERE task_id = #{taskId} ORDER BY created_at DESC")
    List<CollectionLog> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID和状态统计数量
     */
    @Select("SELECT COUNT(*) FROM collection_logs WHERE task_id = #{taskId} AND status = #{status}")
    int countByTaskIdAndStatus(@Param("taskId") Long taskId, @Param("status") String status);

    /**
     * 删除7天前的日志
     */
    @Select("DELETE FROM collection_logs WHERE created_at < #{beforeDate}")
    int deleteOldLogs(@Param("beforeDate") LocalDateTime beforeDate);
}
