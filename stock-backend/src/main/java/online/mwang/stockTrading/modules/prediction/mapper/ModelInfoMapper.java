package online.mwang.stockTrading.modules.prediction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.modules.prediction.entity.ModelInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 模型信息 Mapper
 */
@Mapper
public interface ModelInfoMapper extends BaseMapper<ModelInfo> {

    @Update("update model_info set status = 1 where status = 0")
    void resetStatus();
}
