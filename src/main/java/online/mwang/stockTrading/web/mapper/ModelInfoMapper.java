package online.mwang.stockTrading.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface ModelInfoMapper extends BaseMapper<ModelInfo> {

    @Update("update model_info set status = 1  where status = 0")
    void resetStatus();
}
