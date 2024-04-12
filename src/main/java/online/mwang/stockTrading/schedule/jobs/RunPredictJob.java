package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.po.PredictPrice;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.PredictPriceMapper;
import online.mwang.stockTrading.web.service.PredictPriceService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;
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
        // 从mongo中获取所有股票今天最新价格数据
        final Query query = new Query(Criteria.where("date").is(DateUtils.format1(new Date())));
        final List<StockHistoryPrice> newHistoryPrice = mongoTemplate.find(query, StockHistoryPrice.class);
        // 完成预测后，写入mongo中不同的collection
        List<PredictPrice> predictPrices = newHistoryPrice.stream().map(modelService::modelPredict).collect(Collectors.toList());
        predictPrices.forEach(this::fxiProps);
        predictPrices.forEach(p -> mongoTemplate.insert(p, "predictPrices_" + p.getStockCode()));
        // 更新评分数据
        updateScore(predictPrices);

    }

    private void fxiProps(PredictPrice predictPrice) {
        Date nowDate = new Date();
        predictPrice.setDate(DateUtils.dateFormat.format(nowDate));
        predictPrice.setCreateTime(nowDate);
        predictPrice.setUpdateTime(nowDate);
    }

    private void updateScore(List<PredictPrice> predictPrices) {
        List<StockInfo> stockInfos = stockInfoService.list();
        stockInfos.forEach(s -> predictPrices.stream()
                .filter(p -> p.getStockCode().equals(s.getCode()))
                .findFirst().ifPresent(p -> s.setScore(calculateScore(s, p))));
        stockInfoService.saveBatch(stockInfos);
    }

    // 根据股票价格订单预测值和当前值来计算得分，以平均值计算价格增长率
    private double calculateScore(StockInfo stockInfo, PredictPrice predictPrice) {
        double predictPrice1 = predictPrice.getPredictPrice1();
        double predictPrice2 = predictPrice.getPredictPrice2();
        double predictAvg = (predictPrice1 + predictPrice2) / 2;
        Double price = stockInfo.getPrice();
        return (predictAvg - price) / price * 100 * 10;
    }
}
