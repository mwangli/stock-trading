package online.mwang.foundtrading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.foundtrading.bean.FoundDayRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Mapper
public interface FoundDayMapper extends BaseMapper<FoundDayRecord> {
}
