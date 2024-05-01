package online.mwang.stockTrading.model.predict;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2024/4/28 09:35
 * @description: PredictServiceImpl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictServiceImpl implements IPredictService {

    private final LSTMModel lstmModel;

    @SneakyThrows
    @Override
    public List<StockPrices> modelTrain(List<StockPrices> historyPrices) {
        List<StockPrices> dataList = new ArrayList<>();
        for (int i = 1; i < historyPrices.size(); i++) {
            double todayPrice = historyPrices.get(i).getPrice1();
            double preDayPrice = historyPrices.get(i - 1).getPrice1();
            double increaseRate = preDayPrice == 0 ? 0 : (todayPrice - preDayPrice) / preDayPrice * 100;
            StockPrices stockPrices = historyPrices.get(i);
            // 将数据稳定到[-5,5]之间
//            stockPrices.setIncreaseRate(increaseRate < -1 ? -1 : increaseRate > 1 ? 1 : increaseRate);
            stockPrices.setIncreaseRate(increaseRate);
            dataList.add(stockPrices);
        }
        return lstmModel.train2(dataList);
    }

    @Override
    public StockPrices modelPredict(List<StockPrices> stockPrices) {
        // 找到最新的窗口数据
        if (CollectionUtils.isEmpty(stockPrices)) return null;
        int size = stockPrices.size();
        String code = stockPrices.get(0).getCode();
        String name = stockPrices.get(0).getName();
        log.info("当前股票[{}-{}]预测输入数据集大小:{},最后一组数据:{}", name, code, size, stockPrices.get(size - 1));
        StockPrices predictPrices = lstmModel.predictOneHead(stockPrices);
        if (predictPrices != null)
            log.info("当前股票[{}-{}]预测价格为：{},{}", name, code, predictPrices.getPrice1(), predictPrices.getPrice2());
        return predictPrices;
    }
}
