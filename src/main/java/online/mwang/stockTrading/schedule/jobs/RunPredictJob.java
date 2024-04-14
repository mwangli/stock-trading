package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPredictPrice;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob extends BaseJob {

    private final IModelService modelService;
    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;

    @Value("${PROFILE}")
    private String profile;

    @Override
    void run() {
        // 从mongo中获取所有股票今天最新价格数据
        final Query query = new Query(Criteria.where("date").is(DateUtils.format1(new Date())));
        final List<StockHistoryPrice> newHistoryPrice = mongoTemplate.find(query, StockHistoryPrice.class);
        // 完成预测后，写入mongo中不同的collection
        List<StockPredictPrice> stockPredictPrices = newHistoryPrice.stream().map(modelService::modelPredict).collect(Collectors.toList());
        stockPredictPrices.forEach(this::fxiProps);
        mongoTemplate.insert(stockPredictPrices, StockPredictPrice.class);
        // 更新评分数据
        updateScore(stockPredictPrices);
    }

    private void fxiProps(StockPredictPrice stockTestPrice) {
        Date nowDate = new Date();
        stockTestPrice.setDate(DateUtils.dateFormat.format(nowDate));
    }

    private void updateScore(List<StockPredictPrice> stockPredictPrices) {
        List<StockInfo> stockInfos = stockInfoService.list();
        stockInfos.forEach(s -> stockPredictPrices.stream().filter(p -> p.getCode().equals(s.getCode())).findFirst().ifPresent(p -> {
            s.setScore(calculateScore(s, p));
            s.setPredictPrice((p.getPrice1() + p.getPrice2()) / 2);
            s.setUpdateTime(new Date());
        }));
        stockInfoService.saveOrUpdateBatch(stockInfos);
    }

    // 根据股票价格订单预测值和当前值来计算得分，以平均值计算价格增长率
    private double calculateScore(StockInfo stockInfo, StockPredictPrice stockPredictPrice) {
        double predictPrice1 = stockPredictPrice.getPrice1();
        double predictPrice2 = stockPredictPrice.getPrice2();
        double predictAvg = (predictPrice1 + predictPrice2) / 2;
        Double price = stockInfo.getPrice();
        return (predictAvg - price) / price * 100 * 10;
    }
}
