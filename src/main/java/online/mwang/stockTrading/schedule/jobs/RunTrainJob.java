package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
import online.mwang.stockTrading.model.model.impl.LSTMModelServiceImpl;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunTrainJob extends BaseJob {

    private final IDataService dataService;
    private final LSTMModelServiceImpl LSTMModelServiceImpl;
    private final StockInfoService stockInfoService;


    @Override
    void run() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda();
        queryWrapper.eq(StockInfo::getDeleted, "1");
        queryWrapper.eq(StockInfo::getPermission, "1");
        queryWrapper.between(StockInfo::getPrice, 8, 15);
        List<StockInfo> dataList = stockInfoService.list(queryWrapper);
        StockInfo stockInfo = dataList.get(new Random().nextInt(dataList.size()));
//        dataList.forEach(stockInfo -> {
            log.info("获取到股票待预测股票：{}-{}", stockInfo.getName(), stockInfo.getCode());
            long start = System.currentTimeMillis();
            String stockCode = stockInfo.getCode();
            // 保存股票价格历史数据
//            allJobs.writeHistoryPriceDataToCSV(stockInfo);
            // 训练模型/
            LSTMModelServiceImpl.modelTrain(stockCode);
            long end = System.currentTimeMillis();
            log.info("当前股票：{}-{}，模型任务完成，总共耗时：{}", stockInfo.getName(), stockCode, DateUtils.timeConvertor(end - start));
//        });
    }
}
