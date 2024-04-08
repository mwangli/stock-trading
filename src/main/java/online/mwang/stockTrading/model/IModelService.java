package online.mwang.stockTrading.model;

import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
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

    void modelTrain(List<StockHistoryPrice> historyPrices);

    PredictPrice modelPredict(StockHistoryPrice historyPrice);
}
