package online.mwang.stockTrading.model;

import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    void modelTrain(List<StockHistoryPrice> historyPrices);

    /**
     * 模型预测
     */
    PredictPrice modelPredict(StockHistoryPrice historyPrice);
}
