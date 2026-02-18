package online.mwang.stockTrading.modules.prediction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import online.mwang.stockTrading.modules.prediction.entity.ModelInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;

import java.util.List;

/**
 * 模型信息服务接口
 */
public interface ModelInfoService extends IService<ModelInfo> {

    /**
     * 获取历史价格数据
     */
    List<StockPrices> getHistoryData(List<StockPrices> pricesList);
}
