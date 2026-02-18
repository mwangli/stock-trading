package online.mwang.stockTrading.modules.datacollection.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import online.mwang.stockTrading.modules.datacollection.service.StockInfoService;
import org.springframework.stereotype.Service;

/**
 * 股票信息服务实现
 */
@Service
@RequiredArgsConstructor
public class StockInfoServiceImpl extends ServiceImpl<StockInfoMapper, StockInfo> implements StockInfoService {
}
