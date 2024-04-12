package online.mwang.stockTrading.model;

import online.mwang.stockTrading.web.bean.po.StockPredictPrice;
import online.mwang.stockTrading.web.bean.po.StockTestPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 16:41
 * @description: IModelService
 */
@Service
public interface IModelService {

    /**
     * 模型训练，增量训练或者初始化训练
     */
    List<StockTestPrice> modelTrain(List<StockHistoryPrice> historyPrices, String stockCode);

    /**
     * 模型预测
     */
    StockPredictPrice modelPredict(StockHistoryPrice historyPrice);
}
