package online.mwang.stockTrading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.bean.po.StockInfo;
import online.mwang.stockTrading.mapper.StockInfoMapper;
import online.mwang.stockTrading.service.StockInfoService;
import org.springframework.stereotype.Service;

@Service
public class StockInfoServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockInfoService {

}
