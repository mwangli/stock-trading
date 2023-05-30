package online.mwang.foundtrading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.foundtrading.bean.po.TradingRecord;
import online.mwang.foundtrading.mapper.FoundTradingMapper;
import online.mwang.foundtrading.service.TradingRecordService;
import org.springframework.stereotype.Service;

@Service
public class TradingRecordServiceImpl extends ServiceImpl<FoundTradingMapper, TradingRecord> implements TradingRecordService {
}
