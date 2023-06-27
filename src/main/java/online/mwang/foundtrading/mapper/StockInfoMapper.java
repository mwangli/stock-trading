package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.po.StockInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfo> {

    @Select("select * from stock_info where code = #{code}")
    StockInfo selectByCode(String code);

    @Delete("delete from stock_info where code = #{code}")
    int deleteByCode(String code);
}
