package online.mwang.stockTrading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.bean.po.TradingRecord;
import online.mwang.stockTrading.mapper.FoundTradingMapper;
import online.mwang.stockTrading.service.TradingRecordService;
import org.springframework.stereotype.Service;

@Service
public class TradingRecordServiceImpl extends ServiceImpl<FoundTradingMapper, TradingRecord> implements TradingRecordService {
}
