package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.po.FoundTradingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Mapper
public interface FoundTradingMapper extends BaseMapper<FoundTradingRecord> {

    @Select("select code from found_trading_record where sold = '0'")
    List<String> selectCodes();
}
