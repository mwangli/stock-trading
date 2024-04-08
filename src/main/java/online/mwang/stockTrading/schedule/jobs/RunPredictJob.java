package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.PredictPriceService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob extends BaseJob {

    private final IDataService dataService;
    private final IModelService modelService;
    private final StockInfoService stockInfoService;
    private final PredictPriceMapper predictPriceMapper;
    private final PredictPriceService predictPriceService;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${PROFILE}")
    private String profile;

    @Override
    void run() {
        List<StockInfo> stockInfos = stockInfoService.list();
        // 从mongo中获取所有股票今天最新价格数据
        Query query = new Query(Criteria.where("date").is(DateUtils.format1(new Date())));
        List<StockHistoryPrice> historyPrices = mongoTemplate.find(query, StockHistoryPrice.class);
        // 对每支股票进行价格预测
        List<PredictPrice> predictPrices = historyPrices.stream().map(modelService::modelPredict).collect(Collectors.toList());
        // 填充空余字段
        predictPrices.forEach(this::fxiProps);
        // 写入MySQL
        // TODO 不应该写入MySQL，这个预测数据每天4000条的增量
        log.info("共预测{}条股票数据。",predictPrices.size());
        predictPriceService.saveBatch(predictPrices);
    }

    private void fxiProps(PredictPrice predictPrice){
        Date nowDate = new Date();
        predictPrice.setDate(DateUtils.dateFormat.format(nowDate));
        predictPrice.setCreateTime(nowDate);
        predictPrice.setUpdateTime(nowDate);
    }

//    private void updateScore(String stockCode, double[] predictPrices) {
//        StockInfo stockInfo = stockInfoService.getOne(new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, stockCode));
//        double predictPrice1 = predictPrices[0];
//        double predictPrice2 = predictPrices[1];
//        double predictAvg = (predictPrice1 + predictPrice2) / 2;
//        Double price = stockInfo.getPrice();
//        double score = (predictAvg - price) / price * 100 * 10;
//        stockInfo.setScore(score);
//        stockInfo.setUpdateTime(new Date());
//        stockInfoService.updateById(stockInfo);
//    }
}
