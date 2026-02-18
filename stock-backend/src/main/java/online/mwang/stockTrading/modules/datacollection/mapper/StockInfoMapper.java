package online.mwang.stockTrading.modules.datacollection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 股票信息 Mapper
 */
@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfo> {

    @Select("select * from stock_info where code = #{code}")
    StockInfo getByCode(String code);

    @Select("select name from stock_info where code = #{code}")
    String getNameByCode(String code);

    @Delete("delete from stock_info where code = #{code}")
    int deleteByCode(String code);
}
