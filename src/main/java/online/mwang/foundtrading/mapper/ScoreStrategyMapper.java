package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.po.ScoreStrategy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface ScoreStrategyMapper extends BaseMapper<ScoreStrategy> {

    @Select("select * from score_strategy where status = 1")
    ScoreStrategy getSelectedStrategy();
}
