package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob extends BaseJob {

    private static final int EXAMPLE_LENGTH = 22;
    private static final String VALIDATION_COLLECTION_NAME = "stockPredictPrice";
    private static final String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private final IPredictService modelService;
    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;
    @Value("${PROFILE}")
    private String profile;

    @Override
    void run() {
        // 获取所有股票近一个月的价格信息(mongodb实现分组取最后22条实现较为困难)
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -35);
        String lastMonthDate = DateUtils.dateFormat.format(calendar.getTime());
        Query query = new Query(Criteria.where("date").gte(lastMonthDate));
        List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
        // 在内存中按code进行分组过滤,只保留最后22条数据
        Collection<List<StockPrices>> newHistoryPrices = stockPrices.stream().filter(s -> !Objects.isNull(s.getCode())).collect(Collectors.groupingBy(StockPrices::getCode)).values();
        List<List<StockPrices>> filterHistoryPrices = newHistoryPrices.stream().filter(priceList -> priceList.size() >= EXAMPLE_LENGTH)
                .map(priceList -> priceList = priceList.stream().sorted(Comparator.comparing(StockPrices::getDate))
                        .skip(priceList.size() - EXAMPLE_LENGTH).limit(EXAMPLE_LENGTH).collect(Collectors.toList())).collect(Collectors.toList());
        // 完成预测后，写入mongo
        log.info("获取{}条待预测股票信息。", filterHistoryPrices.size());
        List<StockPrices> stockPredictPrices = filterHistoryPrices.stream().map(modelService::modelPredict).collect(Collectors.toList());
        stockPredictPrices.forEach(predictPrices -> predictPrices.setDate(DateUtils.dateFormat.format(DateUtils.getNextDay(new Date()))));
        mongoTemplate.insert(stockPredictPrices, VALIDATION_COLLECTION_NAME);
        // 更新评分数据
        updateScore(stockPredictPrices);
    }

    private void updateScore(List<StockPrices> stockPredictPrices) {
        List<StockInfo> stockInfos = stockInfoService.list();
        stockInfos.forEach(s -> stockPredictPrices.stream().filter(p -> p.getCode().equals(s.getCode())).findFirst().ifPresent(p -> {
            s.setScore(calculateScore(s, p));
            s.setPredictPrice((p.getPrice1() + p.getPrice2()) / 2);
            s.setUpdateTime(new Date());
        }));
        stockInfoService.saveOrUpdateBatch(stockInfos);
    }

    // 根据股票价格订单预测值和当前值来计算得分，以平均值计算价格增长率
    private double calculateScore(StockInfo stockInfo, StockPrices stockPredictPrice) {
        double predictPrice1 = stockPredictPrice.getPrice1();
        double predictPrice2 = stockPredictPrice.getPrice2();
        double predictAvg = (predictPrice1 + predictPrice2) / 2;
        Double price = stockInfo.getPrice();
        return (predictAvg - price) / price * 100 * 10;
    }
}

