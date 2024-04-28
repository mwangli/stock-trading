package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        // 获取待预测股票
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(StockInfo::getCode);
        queryWrapper.orderByDesc(StockInfo::getPrice);
        final List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        log.info("共获取{}条待预测股票.", stockInfoList.size());
        // 获取所有股票近一个月的价格信息
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -35);
        String lastMonthDate = DateUtils.dateFormat.format(calendar.getTime());
        Query query = new Query(Criteria.where("date").gte(lastMonthDate).and("code").ne(null));
        List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
        // 在内存中进行分组过滤吗，每组股票取最新的22条数据
        List<List<StockPrices>> dataList = stockInfoList.stream().map(stockInfo -> stockPrices.stream().filter(p -> p.getCode().equals(stockInfo.getCode())).collect(Collectors.toList()))
                .filter(pricesList -> pricesList.size() >= EXAMPLE_LENGTH).map(pricesList -> pricesList = pricesList.stream().sorted(Comparator.comparing(StockPrices::getDate))
                        .skip(stockPrices.size() - EXAMPLE_LENGTH).limit(EXAMPLE_LENGTH).collect(Collectors.toList())).collect(Collectors.toList());

        List<StockPrices> predictPrices = dataList.stream().map(modelService::modelPredict).collect(Collectors.toList());
        String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
        predictPrices.forEach(priceList -> fixProps(priceList, stockInfoList, date));
        mongoTemplate.remove(new Query(Criteria.where("date").is(date)), StockPrices.class, VALIDATION_COLLECTION_NAME);
        mongoTemplate.insert(predictPrices, VALIDATION_COLLECTION_NAME);
        // 更新评分数据
        updateScore(predictPrices);
    }

    private void fixProps(StockPrices stockPredictPrices, List<StockInfo> stockInfos, String date) {
        stockPredictPrices.setDate(date);
        stockPredictPrices.setName(stockInfos.stream().filter(stockInfo -> stockInfo.getCode().equals(stockPredictPrices.getCode())).findFirst().orElse(new StockInfo()).getName());
    }

    private void updateScore(List<StockPrices> stockPredictPrices) {
        List<StockInfo> stockInfos = stockInfoService.list();
        ArrayList<StockInfo> updateList = new ArrayList<>();
        stockPredictPrices.forEach(p -> stockInfos.stream().filter(s -> s.getCode().equals(p.getCode())).findFirst().ifPresent(s -> {
            s.setScore(calculateScore(s, p));
            s.setPredictPrice((p.getPrice1() + p.getPrice2()) / 2);
            s.setUpdateTime(new Date());
            updateList.add(s);
        }));
        stockInfoService.saveOrUpdateBatch(updateList);
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

