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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author 13255
 */
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
        // 获取股票信息和模型信息
        List<StockInfo> stockInfos = stockInfoService.list();
        List<ModelInfo> modelInfos = modelInfoService.list();
        ArrayList<StockPrices> predictPrices = new ArrayList<>();
        String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
        for (StockInfo stockInfo : stockInfos) {
            // 获取历史价格
            if (modelInfos.stream().noneMatch(m -> m.getCode().equals(stockInfo.getCode()))) continue;
            Query query = new Query(Criteria.where("code").is(stockInfo.getCode())).with(Sort.by(Sort.Direction.DESC, "date")).limit(EXAMPLE_LENGTH);
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            if (stockHistoryPrices.size() != EXAMPLE_LENGTH) continue;
            Collections.reverse(stockHistoryPrices);
            // 预填充最后一个预测数据以方便构造输入数据集
            stockHistoryPrices.add(new StockPrices(0.0));
            StockPrices predictPrice = modelService.modelPredict(stockHistoryPrices);
            if (predictPrice == null) continue;
            predictPrice.setDate(date);
            predictPrices.add(predictPrice);
            log.info("当前股票[{}-{}],{}预测价格为:{}", predictPrice.getCode(), predictPrice.getName(), date, predictPrice.getPrice1());
        }
        mongoTemplate.remove(new Query(Criteria.where("date").is(date)), VALIDATION_COLLECTION_NAME);
        mongoTemplate.insert(predictPrices, VALIDATION_COLLECTION_NAME);
        log.info("共写入{}条股票价格预测数据。", predictPrices.size());
        updateStockScore(stockInfos, modelInfos);
    }

    private void updateStockScore(List<StockInfo> stockInfos, List<ModelInfo> modelInfos) {
        // 获取预测结果中日期大于等于今天的数据(2条)
        Query query = new Query(Criteria.where("date").gte(DateUtils.format1(new Date()))).with(Sort.by(Sort.Direction.DESC, "date"));
        List<StockPrices> predictPriceList = mongoTemplate.find(query, StockPrices.class, VALIDATION_COLLECTION_NAME);
        ArrayList<StockInfo> updateList = new ArrayList<>();
        predictPriceList.stream().collect(Collectors.groupingBy(StockPrices::getCode)).forEach((code, pricesList) -> {
            if (pricesList.size() >= 2) {
                Double curPrice = pricesList.get(0).getPrice1();
                Double prePrice = pricesList.get(1).getPrice1();
                double increaseRate = prePrice == 0 ? 0 : (curPrice - prePrice) / prePrice;
                double score2 = increaseRate * 100 * 10;
                AtomicReference<Double> score1 = new AtomicReference<>((double) 0);
                modelInfos.stream().filter(m -> m.getCode().equals(code)).findFirst().ifPresent(m -> score1.set(m.getScore()));
                stockInfos.stream().filter(s -> s.getCode().equals(code)).findFirst().ifPresent(s -> {
                    s.setScore(score1.get() * 0.5 + score2 * 0.5);
                    updateList.add(s);
                });
            }
        });
        stockInfoService.updateBatchById(updateList);
        log.info("共更新{}条股票评分数据。", updateList.size());
    }
}

