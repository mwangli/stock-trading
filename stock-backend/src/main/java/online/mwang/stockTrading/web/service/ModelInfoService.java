package online.mwang.stockTrading.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:57
 * @description: FoundTradingService
 */
@Service
public interface ModelInfoService extends IService<ModelInfo> {

    /**
     * 获取历史价格数据
     */
    List<StockPrices> getHistoryData(List<StockPrices> pricesList);
}
