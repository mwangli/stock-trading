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

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
        final List<StockInfo> list = stockInfoService.list(queryWrapper);
        log.info("共获取{}条待预测股票.", list.size());
        // 获取所有股票近一个月的价格信息
        for (StockInfo s : list) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.DATE, -35);
            String lastMonthDate = DateUtils.dateFormat.format(calendar.getTime());
            Query query = new Query(Criteria.where("date").gte(lastMonthDate).and("code").is(s.getCode()));
            List<StockPrices> stockPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            if (stockPrices.size() >= EXAMPLE_LENGTH) {
                stockPrices = stockPrices.stream().sorted(Comparator.comparing(StockPrices::getDate)).skip(stockPrices.size() - EXAMPLE_LENGTH).limit(EXAMPLE_LENGTH).collect(Collectors.toList());
                StockPrices stockPredictPrices = modelService.modelPredict(stockPrices);
                if (stockPredictPrices == null) continue;
                String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
                stockPredictPrices.setDate(date);
                stockPredictPrices.setName(list.stream().filter(stockInfo -> stockInfo.getCode().equals(s.getCode())).findFirst().orElse(new StockInfo()).getName());
                mongoTemplate.remove(new Query(Criteria.where("code").is(s.getCode()).and("date").is(date)), StockPrices.class, VALIDATION_COLLECTION_NAME);
                mongoTemplate.insert(stockPredictPrices, VALIDATION_COLLECTION_NAME);
                // 更新评分数据
                updateScore(stockPredictPrices);
            }
        }
    }


    private void updateScore(StockPrices stockPredictPrices) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getCode, stockPredictPrices.getCode());
        StockInfo stockInfo = stockInfoService.getOne(queryWrapper);
        stockInfo.setScore(calculateScore(stockInfo, stockPredictPrices));
        stockInfo.setPredictPrice((stockPredictPrices.getPrice1() + stockPredictPrices.getPrice2()) / 2);
        stockInfo.setUpdateTime(new Date());
        stockInfoService.saveOrUpdate(stockInfo);
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

