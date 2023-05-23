package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.FoundDayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Mapper
public interface FoundDayMapper extends BaseMapper<FoundDayRecord> {

    @Select("select * from found_day_record where today_date = #{date} order by daily_income_rate desc limit 0,1")
    FoundDayRecord selectBest(String date);
}
