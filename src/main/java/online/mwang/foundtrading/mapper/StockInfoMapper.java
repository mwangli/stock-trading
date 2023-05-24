package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.po.StockInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 11:13
 * @description: StockInfoMapper
 */
@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfo> {

//    @Select("select code from stock_info")
//    List<String> selectCodes();
//
//    @Select("select * from stock_info where price <= 12 and price >= 8 order by score desc limit 0 ,30")
//    List<StockInfo> selectBest();
}
