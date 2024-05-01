package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
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

    @Override
    void run() {
        // 获取所有股票近一个月的价格信息(mongodb实现分组取最后22条实现较为困难)
        Date preMonthDate = DateUtils.getNextDay(new Date(), -35);
        String lastMonthDate = DateUtils.dateFormat.format(preMonthDate);
        Query query = new Query(Criteria.where("date").gte(lastMonthDate));
        List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
        // 在内存中按code进行分组过滤,只保留最后22条数据
        Collection<List<StockPrices>> newHistoryPrices = stockPrices.stream().filter(s -> !Objects.isNull(s.getCode())).collect(Collectors.groupingBy(StockPrices::getCode)).values();
        List<List<StockPrices>> filterHistoryPrices = newHistoryPrices.stream().filter(priceList -> priceList.size() >= EXAMPLE_LENGTH)
                .map(priceList -> priceList = priceList.stream().sorted(Comparator.comparing(StockPrices::getDate))
                        .skip(priceList.size() - EXAMPLE_LENGTH).limit(EXAMPLE_LENGTH).collect(Collectors.toList())).collect(Collectors.toList());
        // 价格预测,保存数据
        ArrayList<StockPrices> predictPrices = new ArrayList<>();
        for (List<StockPrices> newHistoryPrice : filterHistoryPrices) {
            StockPrices predictPrice = modelService.modelPredict(newHistoryPrice);
            if (predictPrice != null) predictPrices.add(predictPrice);
        }
        // 更新评分数据
        List<StockInfo> stockInfos = stockInfoService.list();
        String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
        List<StockPrices> dataList = predictPrices.stream().map(p -> fixProps(p, stockInfos, date)).collect(Collectors.toList());
        log.info("预测价格数据：{}条", dataList.size());
        updateScore(stockInfos, dataList);
        // 保存预测数据
        mongoTemplate.remove(new Query(Criteria.where("date").is(date)), StockPrices.class, VALIDATION_COLLECTION_NAME);
        mongoTemplate.insert(dataList, VALIDATION_COLLECTION_NAME);
    }

    private StockPrices fixProps(StockPrices stockPredictPrices, List<StockInfo> stockInfos, String date) {
        stockPredictPrices.setDate(date);
        stockPredictPrices.setName(stockInfos.stream().filter(stockInfo -> stockInfo.getCode().equals(stockPredictPrices.getCode())).findFirst().orElse(new StockInfo()).getName());
        return stockPredictPrices;
    }

    private void updateScore(List<StockInfo> stockInfos, List<StockPrices> stockPredictPrices) {
        ArrayList<StockInfo> updateList = new ArrayList<>();
        stockPredictPrices.forEach(p -> stockInfos.stream().filter(s -> s.getCode().equals(p.getCode())).findFirst().ifPresent(s -> {
            s.setScore(calculateScore(s, p));
            s.setPredictPrice(p.getPrice1());
            s.setUpdateTime(new Date());
            updateList.add(s);
        }));
        stockInfoService.updateBatchById(updateList);
    }

    // 根据股票价格订单预测值和当前值来计算得分
    private double calculateScore(StockInfo stockInfo, StockPrices stockPredictPrice) {
        double predictPrice1 = stockPredictPrice.getPrice1();
        double nowPrice = stockInfo.getPrice();
        return (predictPrice1 - nowPrice) / nowPrice * 100 * 10;
    }
}

