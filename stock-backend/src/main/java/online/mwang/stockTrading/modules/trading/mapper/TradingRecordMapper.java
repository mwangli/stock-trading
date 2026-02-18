package online.mwang.stockTrading.modules.trading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import online.mwang.stockTrading.modules.trading.entity.TradingRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易记录 Mapper
 */
@Mapper
public interface TradingRecordMapper extends BaseMapper<TradingRecord> {

    TradingRecord getByCodeAndDate(String code, String buyDateString);
}
