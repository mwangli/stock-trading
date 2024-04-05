package online.mwang.stockTrading.web.job;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.predict.LSTMModel;
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


    @SneakyThrows
    @Scheduled(fixedDelay = Long.MAX_VALUE)
    private void test() {
        List<String> stockCodeList = Arrays.asList("600114", "002527", "600272");
        String stockCode = stockCodeList.get(0);
        // 保存股票价格历史数据
        allJobs.writeHistoryPriceData(stockCode);
        // 训练模型/
        lstmModel.modelTrain(stockCode);
        // 预测价格
        double newPrice = 6.5;
        double v = lstmModel.modelPredict(stockCode, 6.5);
    }

}
