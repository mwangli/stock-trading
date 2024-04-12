package online.mwang.stockTrading.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import online.mwang.stockTrading.web.bean.po.StockTestPrice;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.service.PredictPriceService;
import org.springframework.stereotype.Service;

@Service
public class PredictPriceServiceImpl extends ServiceImpl<PredictPriceMapper, StockTestPrice> implements PredictPriceService {

}
