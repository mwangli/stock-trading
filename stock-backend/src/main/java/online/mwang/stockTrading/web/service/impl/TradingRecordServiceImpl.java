package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.TradingRecordMapper;
import online.mwang.stockTrading.web.service.TradingRecordService;
import org.springframework.stereotype.Service;

@Service
public class TradingRecordServiceImpl extends ServiceImpl<TradingRecordMapper, TradingRecord> implements TradingRecordService {
}
