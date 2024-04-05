package online.mwang.stockTrading.web.job;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.predict.LSTMModel;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TestJob {

    private final AllJobs allJobs;
    private final LSTMModel lstmModel;
    private final StockInfoService stockInfoService;


    @SneakyThrows
    @Scheduled(fixedDelay = Long.MAX_VALUE)
    private void test() {
        List<StockInfo> stockInfoList = stockInfoService.list();
        List<String> stockCodeList = Arrays.asList("600114", "002527", "600272");
        stockInfoList.stream().filter(s -> stockCodeList.contains(s.getCode())).forEach(stockInfo -> {
            long start = System.currentTimeMillis();
            String stockCode = stockInfo.getCode();
            // 保存股票价格历史数据
            allJobs.writeHistoryPriceData(stockCode);
            // 训练模型/
            lstmModel.modelTrain(stockCode);
            // 预测价格
            double newPrice = stockInfo.getPrice();
            double predictNextPrice = lstmModel.modelPredict(stockCode, newPrice);
            // 将预测数据写回到数据中
            stockInfo.setPredictPrice(predictNextPrice);
            stockInfoService.save(stockInfo);
            long end = System.currentTimeMillis();
            log.info("当前股票：{}-{}，价格预测任务完成，耗时：{}秒", stockInfo.getName(), stockCode, DateUtils.timeConvertor(end - start));
        });
    }
}
