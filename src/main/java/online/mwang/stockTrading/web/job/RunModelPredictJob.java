package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.predict.LSTMModel;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunModelPredictJob extends BaseJob{

    private final AllJobs allJobs;
    private final LSTMModel lstmModel;
    private final StockInfoService stockInfoService;
    private final PredictPriceMapper predictPriceMapper;


    @SneakyThrows
    @Scheduled(fixedDelay = Long.MAX_VALUE)
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
            // 预测价格
            double newPrice = stockInfo.getPrice();
            double predictNextPrice = lstmModel.modelPredict(stockCode, newPrice);
            // 将预测数据写入数据库以备后续观察分析
            PredictPrice predictPrice = new PredictPrice();
            predictPrice.setStockCode(stockCode);
            Date nowDate = new Date();
            predictPrice.setDate(DateUtils.dateFormat.format(nowDate));
            predictPrice.setPredictPrice(predictNextPrice);
            predictPrice.setCreateTime(nowDate);
            predictPrice.setUpdateTime(nowDate);
            predictPriceMapper.insert(predictPrice);
            long end = System.currentTimeMillis();
            log.info("当前股票：{}-{}，价格预测任务完成，总共耗时：{}", stockInfo.getName(), stockCode, DateUtils.timeConvertor(end - start));
    }

    @Override
    void run(String runningId) {
        runJob();
    }
}
