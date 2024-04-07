package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.model.StockPricePrediction;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob extends BaseJob {

    private final AllJobs allJobs;
    private final StockPricePrediction stockPricePrediction;
    private final StockInfoService stockInfoService;
    private final PredictPriceMapper predictPriceMapper;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${PROFILE}")
    private String profile;

    @Override
    void run() {
        Set<String> keySet = redisTemplate.keys(profile + "_trainedStockList_*");
        assert keySet != null;
        keySet.forEach(key -> {
            String[] split = key.split("_");
            String stockCode = split[2];
            log.info("获取到股票待预测股票：{}", stockCode);
            long start = System.currentTimeMillis();
            // 获取最新的两条价格数据
            double newPrice1 = 0;
            double newPrice2 = 0;
            // 每只股票写入不同的表
            String collectionName = "code_" + stockCode;
            StockHistoryPrice one = mongoTemplate.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "date")), StockHistoryPrice.class, collectionName);
            if (one != null) {
                newPrice1 = one.getPrice1();
                newPrice2 = one.getPrice2();
            }
            double[] predictPrices = stockPricePrediction.modelPredict(stockCode, newPrice1, newPrice2);
            log.info("当前股票[{}-{}]预测价格为：{}", one.getName(), one.getCode(), predictPrices);
            // 将预测数据写入数据库以备后续观察分析
            PredictPrice predictPrice = new PredictPrice();
            predictPrice.setStockCode(stockCode);
            Date nowDate = new Date();
            predictPrice.setDate(DateUtils.dateFormat.format(nowDate));
            predictPrice.setPredictPrice1(predictPrices[0]);
            predictPrice.setPredictPrice2(predictPrices[1]);
            predictPrice.setCreateTime(nowDate);
            predictPrice.setUpdateTime(nowDate);
            predictPriceMapper.insert(predictPrice);
            // 更新股票数据的得分
            updateScore(stockCode, predictPrices);
            long end = System.currentTimeMillis();
            log.info("当前股票：{}，价格预测任务完成，总共耗时：{}", stockCode, DateUtils.timeConvertor(end - start));
        });
    }

    private void updateScore(String stockCode, double[] predictPrices) {
        StockInfo stockInfo = stockInfoService.getOne(new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, stockCode));
        double predictPrice1 = predictPrices[0];
        double predictPrice2 = predictPrices[1];
        double predictAvg = (predictPrice1 + predictPrice2) / 2;
        Double price = stockInfo.getPrice();
        double score = (predictAvg - price) / price * 100 * 10;
        stockInfo.setScore(score);
        stockInfo.setUpdateTime(new Date());
        stockInfoService.updateById(stockInfo);
    }
}
