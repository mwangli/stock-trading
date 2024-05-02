package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.domain.Sort;
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
    private final ModelInfoService modelInfoService;
    private final MongoTemplate mongoTemplate;

    @Override
    void run() {
        // 获取所有股票近一个月的价格信息(mongodb实现分组取最后22条实现较为困难)
        Date preMonthDate = DateUtils.getNextDay(new Date(), -35);
        String lastMonthDate = DateUtils.dateFormat.format(preMonthDate);
        Query query = new Query(Criteria.where("date").gte(lastMonthDate)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
        // 在内存中按code进行分组过滤,只保留最后22条数据
        Collection<List<StockPrices>> newHistoryPrices = stockPrices.stream().filter(s -> !Objects.isNull(s.getCode())).collect(Collectors.groupingBy(StockPrices::getCode)).values();
        List<List<StockPrices>> filterHistoryPrices = newHistoryPrices.stream().filter(priceList -> priceList.size() >= EXAMPLE_LENGTH)
                .map(priceList -> priceList = priceList.stream().sorted(Comparator.comparing(StockPrices::getDate))
                        .skip(priceList.size() - EXAMPLE_LENGTH).limit(EXAMPLE_LENGTH).collect(Collectors.toList())).collect(Collectors.toList());
        // 价格预测,保存数据
        ArrayList<StockPrices> predictPrices = new ArrayList<>();
        for (List<StockPrices> pricesList : filterHistoryPrices) {
            // 预填充最后一个预测数据以方便构造输入数据集
            pricesList.add(new StockPrices(0.0));
            StockPrices predictPrice = modelService.modelPredict(pricesList);
            if (predictPrice != null) predictPrices.add(predictPrice);
        }
        List<StockInfo> stockInfos = stockInfoService.list();
        String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
        List<StockPrices> dataList = predictPrices.stream().map(p -> fixProps(p, stockInfos, date)).collect(Collectors.toList());
        log.info("预测价格数据：{}", dataList);
        // 保存预测数据
        mongoTemplate.remove(new Query(Criteria.where("date").is(date)), StockPrices.class, VALIDATION_COLLECTION_NAME);
        mongoTemplate.insert(dataList, VALIDATION_COLLECTION_NAME);
        // 更新股票
        updateStockScore(stockInfos);
    }

    private StockPrices fixProps(StockPrices stockPredictPrices, List<StockInfo> stockInfos, String date) {
        stockPredictPrices.setDate(date);
        stockPredictPrices.setName(stockInfos.stream().filter(stockInfo -> stockInfo.getCode().equals(stockPredictPrices.getCode())).findFirst().orElse(new StockInfo()).getName());
        return stockPredictPrices;
    }

    private void updateStockScore(List<StockInfo> stockInfos) {
        // 获取预测结果中日期大于等于今天的数据(2条)
        Query query = new Query(Criteria.where("date").gte(new Date())).with(Sort.by(Sort.Direction.DESC, "date"));
        List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class, VALIDATION_COLLECTION_NAME);
        // 获取所有的模型信息
        List<ModelInfo> modelInfos = modelInfoService.list();
        // 按code进行分组，计算日增长率
        Collection<List<StockPrices>> predictStockPrices = stockPrices.stream().filter(s -> !Objects.isNull(s.getCode())).collect(Collectors.groupingBy(StockPrices::getCode)).values();
        ArrayList<StockInfo> updateScoreList = new ArrayList<>();
        predictStockPrices.forEach(priceList -> {
            if (priceList.size() >= 2) {
                String code = priceList.get(0).getCode();
                Double curPrice = priceList.get(0).getPrice1();
                Double prePrice = priceList.get(1).getPrice1();
                double increaseRate = prePrice == 0 ? 0 : (curPrice - prePrice) / prePrice;
                double score2 = increaseRate * 100 * 10;
                ModelInfo modelInfo = modelInfos.stream().filter(m -> m.getCode().equals(code)).findFirst().orElse(new ModelInfo());
                double score1 = modelInfo.getScore() == null ? 0 : modelInfo.getScore();
                double finalScore = calculateScore(score1, score2, 0.5, 0.5);
                stockInfos.stream().filter(s -> s.getCode().equals(code)).findFirst().ifPresent(s -> {
                    s.setScore(finalScore);
                    s.setUpdateTime(new Date());
                    updateScoreList.add(s);
                });
            }
        });
        log.info("更新{}条股票评分数据。", updateScoreList.size());
        stockInfoService.updateBatchById(updateScoreList);
    }

    // 使用模型准确率评分和增长率评分的加权和作为最终评分
    private double calculateScore(double score1, double score2, double w1, double w2) {
        return score1 * w1 + score2 * w2;
    }
}

