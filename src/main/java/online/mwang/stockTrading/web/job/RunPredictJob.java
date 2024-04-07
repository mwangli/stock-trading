package online.mwang.stockTrading.web.job;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.predict.model.StockPricePrediction;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
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
            Query query = new Query(Criteria.where("date").is(DateUtils.format1(new Date())));
            // 每只股票写入不同的表
            String collectionName = "code_" + stockCode;
            StockHistoryPrice one = mongoTemplate.findOne(query, StockHistoryPrice.class, collectionName);
            if (one != null) {
                newPrice1 = one.getPrice1();
                newPrice2 = one.getPrice2();
            }
            double[] predictPrices = stockPricePrediction.modelPredict(stockCode, newPrice1, newPrice2);
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
            long end = System.currentTimeMillis();
            log.info("当前股票：{}，价格预测任务完成，总共耗时：{}", stockCode, DateUtils.timeConvertor(end - start));
        });
    }


}
