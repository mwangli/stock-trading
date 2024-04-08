package online.mwang.stockTrading.model.model;

import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import org.springframework.stereotype.Service;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 16:41
 * @description: IModelService
 */
@Service
public interface IModelService {

    void modelTrain(String stockCode);

    double[] modelPredict(StockHistoryPrice historyPrice);
}
