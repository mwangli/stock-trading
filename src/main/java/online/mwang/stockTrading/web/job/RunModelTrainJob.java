package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.model.StockPricePrediction;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunModelTrainJob extends BaseJob {

    private final AllJobs allJobs;
    private final StockPricePrediction stockPricePrediction;
    private final StockInfoService stockInfoService;


    @SneakyThrows
    @Scheduled(fixedDelay = Long.MAX_VALUE)
    private void runJob() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda();
        queryWrapper.eq(StockInfo::getDeleted, "1");
        queryWrapper.eq(StockInfo::getPermission, "1");
        queryWrapper.between(StockInfo::getPrice, 8, 15);
        List<StockInfo> dataList = stockInfoService.list(queryWrapper);
        StockInfo stockInfo = dataList.get(new Random().nextInt(100));
        log.info("获取到股票待预测股票：{}-{}", stockInfo.getName(), stockInfo.getCode());

//        });
//        dataList.stream().findAny(stockInfo -> {
        long start = System.currentTimeMillis();
        String stockCode = stockInfo.getCode();
        // 保存股票价格历史数据
        allJobs.writeHistoryPriceDataToCSV(stockCode);
        // 训练模型/
        stockPricePrediction.modelTrain(stockCode);
        long end = System.currentTimeMillis();
        log.info("当前股票：{}-{}，模型任务完成，总共耗时：{}", stockInfo.getName(), stockCode, DateUtils.timeConvertor(end - start));
    }

    @Override
    void run(String runningId) {
        runJob();
    }
}
