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

@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob {

    private final AllJobs allJobs;
    private final LSTMModel lstmModel;
    private final StockInfoService stockInfoService;
    private final PredictPriceMapper predictPriceMapper;


    @SneakyThrows
    @Scheduled(fixedDelay = Long.MAX_VALUE)
    private void test() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda().eq(StockInfo::getDeleted, "1")
                .eq(StockInfo::getPermission, "1").between(StockInfo::getPrice, 8, 15);
        Page<StockInfo> page = stockInfoService.page(Page.of(0, 10), queryWrapper);
        List<StockInfo> dataList = page.getRecords();
        log.info("获取到股票待预测股票数量：{}", dataList.size());
        dataList.forEach(stockInfo -> {
            long start = System.currentTimeMillis();
            String stockCode = stockInfo.getCode();
            // 保存股票价格历史数据
            allJobs.writeHistoryPriceData(stockCode);
            // 训练模型/
            lstmModel.modelTrain(stockCode);
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
            log.info("当前股票：{}-{}，价格预测任务完成，总共耗时：{}秒", stockInfo.getName(), stockCode, DateUtils.timeConvertor(end - start));
        });
    }
}
