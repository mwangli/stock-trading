package online.mwang.stockTrading.model.predict;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.model.representation.StockData;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

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
        List<StockData> dataList = historyPrices.stream().map(s -> {
            StockData stockData = new StockData();
            stockData.setDate(s.getDate());
            stockData.setCode(s.getCode());
            stockData.setName(s.getName());
            stockData.setOpen(s.getPrice1() == null ? 0 : s.getPrice1());
            stockData.setClose(s.getPrice2() == null ? 0 : s.getPrice2());
            stockData.setHigh(s.getPrice3() == null ? 0 : s.getPrice3());
            stockData.setLow(s.getPrice4() == null ? 0 : s.getPrice4());
            return stockData;
        }).collect(Collectors.toList());
        return lstmModel.train(dataList);
    }

    @Override
    public StockPrices modelPredict(List<StockPrices> stockPrices) {
        // 找到最新的窗口数据
        if (CollectionUtils.isEmpty(stockPrices)) throw new BusinessException("历史数据为空，无法进行预测!");
        int size = stockPrices.size();
        String code = stockPrices.get(0).getCode();
        String name = stockPrices.get(0).getName();
        log.info("当前股票[{}-{}]预测数据集输入:{},第一组数据:{},最后一组数据:{}", name, code, size, stockPrices.get(0), stockPrices.get(size - 1));
        StockPrices predictPrices = lstmModel.predictOneHead(stockPrices);
        if (predictPrices != null)
            log.info("当前股票[{}-{}]预测价格为：{},{}", name, code, predictPrices.getPrice1(), predictPrices.getPrice2());
        return predictPrices;
    }
}
