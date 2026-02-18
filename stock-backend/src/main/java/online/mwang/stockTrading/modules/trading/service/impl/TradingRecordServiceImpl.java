package online.mwang.stockTrading.modules.trading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.modules.trading.entity.TradingRecord;
import online.mwang.stockTrading.modules.trading.mapper.TradingRecordMapper;
import online.mwang.stockTrading.modules.trading.service.TradingRecordService;
import org.springframework.stereotype.Service;

/**
 * 交易记录服务实现
 */
@Service
public class TradingRecordServiceImpl extends ServiceImpl<TradingRecordMapper, TradingRecord> implements TradingRecordService {
}
