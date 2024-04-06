package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.predict.LSTMModel;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
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
    private final LSTMModel lstmModel;
    private final StockInfoService stockInfoService;
    private final PredictPriceMapper predictPriceMapper;


    @SneakyThrows
//    @Scheduled(fixedDelay = Long.MAX_VALUE)
    private void runJob() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getDeleted, "1")
                .eq(StockInfo::getPermission, "1").between(StockInfo::getPrice, 8, 15);
        Page<StockInfo> page = stockInfoService.page(Page.of(0, 500), queryWrapper);
        List<StockInfo> dataList = page.getRecords();
        StockInfo stockInfo = dataList.get(new Random().nextInt(100));
        log.info("获取到股票待预测股票：{}-{}", stockInfo.getName(), stockInfo.getCode());

//        });
//        dataList.stream().findAny(stockInfo -> {
        long start = System.currentTimeMillis();
        String stockCode = stockInfo.getCode();
        // 保存股票价格历史数据
        allJobs.writeHistoryPriceDataToCSV(stockCode);
        // 训练模型/
        lstmModel.modelTrain(stockCode);
        long end = System.currentTimeMillis();
        log.info("当前股票：{}-{}，模型任务完成，总共耗时：{}", stockInfo.getName(), stockCode, DateUtils.timeConvertor(end - start));
    }

    @Override
    void run(String runningId) {
        runJob();
    }
}
