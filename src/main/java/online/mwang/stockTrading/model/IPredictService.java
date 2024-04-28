package online.mwang.stockTrading.model;

import online.mwang.stockTrading.web.bean.po.StockPrices;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/8 16:41
 * @description: IModelService
 */
@Service
public interface IPredictService {

    /**
     * 模型训练,返回测试集结果
     */
    List<StockPrices> modelTrain(List<StockPrices> historyPrices);

    /**
     * T+1模型预测
     */
    StockPrices modelPredict(List<StockPrices> historyPrices);
}
