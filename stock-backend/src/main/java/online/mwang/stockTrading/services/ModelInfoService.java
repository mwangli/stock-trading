package online.mwang.stockTrading.services;

import online.mwang.stockTrading.entities.ModelInfo;
import online.mwang.stockTrading.entities.StockPrices;

import java.util.List;

/**
 * 模型信息服务接口
 */
public interface ModelInfoService {

    void save(ModelInfo modelInfo);

    ModelInfo findById(Long id);

    ModelInfo update(ModelInfo modelInfo);

    void delete(ModelInfo modelInfo);

    List<ModelInfo> findAll();

    void resetStatus();

    Long count();

    /**
     * 获取历史价格数据
     */
    List<StockPrices> getHistoryData(List<StockPrices> pricesList);
}
