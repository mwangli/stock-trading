package online.mwang.stockTrading.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:57
 * @description: FoundTradingService
 */
@Service
public interface StockInfoService extends IService<StockInfo> {

    List<StockInfo> getTrainStockInfos();
}
