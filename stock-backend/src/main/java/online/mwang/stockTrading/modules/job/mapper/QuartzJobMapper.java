package online.mwang.stockTrading.modules.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.modules.job.entity.QuartzJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * Quartz任务 Mapper
 */
@Mapper
public interface QuartzJobMapper extends BaseMapper<QuartzJob> {

    @Update("update quartz_job set running = 0")
    void resetRunningStatus();
}
