package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunTrainJob extends BaseJob {

    private static final String TEST_COLLECTION_NAME = "stockTestPrice";
    private static final String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private final IPredictService modelService;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final StockInfoService stockInfoService;
    private final StockInfoMapper stockInfoMapper;
    private final ModelInfoService modelInfoService;
    private boolean isInterrupted = false;

    @Override
    public void interrupt() {
        log.info("正在尝试终止模型训练任务...");
        isInterrupted = true;
    }

    @SneakyThrows
    @Override
    void run() {
        List<StockInfo> stockInfos = stockInfoService.list();
        for (StockInfo s : stockInfos) {
            if (isInterrupted) throw new BusinessException("模型训练任务已终止！");
            if (!debug && !DateUtils.isWeekends(new Date()) && DateUtils.inTradingTimes1()) break;
            if (redisTemplate.opsForValue().get("model:code:" + s.getCode()) != null) continue;
            redisTemplate.opsForValue().set("model:code:" + s.getCode(), s.getCode(), 30, TimeUnit.DAYS);
            String stockCode = s.getCode();
            String stockName = s.getName();
            final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            if (stockHistoryPrices.size() < 100) continue;
            log.info("股票[{}-{}],训练数据集大小为:{}", stockName, stockCode, stockHistoryPrices.size());
            List<StockPrices> stockTestPrices = modelService.modelTrain(stockHistoryPrices);
            final Query deleteQuery = new Query(Criteria.where("code").is(stockCode));
            List<Object> removed = mongoTemplate.findAllAndRemove(deleteQuery, TEST_COLLECTION_NAME);
            mongoTemplate.insert(stockTestPrices, TEST_COLLECTION_NAME);
            log.info("股票[{}-{}],清空{}条，写入{}条测试集数据", stockName, stockCode, removed.size(), stockTestPrices.size());
            // 更新模型评分
            updateModelScore(stockTestPrices);
        }
    }

    private void updateModelScore(List<StockPrices> stockTestPrices) {
        // 获取历史数据
        String maxDate = stockTestPrices.stream().map(StockPrices::getDate).max(String::compareTo).orElse("");
        String minDate = stockTestPrices.stream().map(StockPrices::getDate).min(String::compareTo).orElse("");
        String stockCode = stockTestPrices.get(0).getCode();
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate));
        List<StockPrices> historyPrices = mongoTemplate.find(historyQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        setIncreaseRate(historyPrices);
        setIncreaseRate(stockTestPrices);
        // 计算测试集误差和评分
        int mistakeCount = 0;
        for (int i = 1; i < stockTestPrices.size(); i++) {
            Double testIncrease = stockTestPrices.get(i).getIncreaseRate();
            Double actualIncrease = historyPrices.get(i).getIncreaseRate();
            if (hasMistake(testIncrease, actualIncrease)) mistakeCount++;
        }
        double testDeviation = (double) mistakeCount / stockTestPrices.size();
        double score = (1 - testDeviation) * 100;
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelInfo::getCode, stockCode);
        ModelInfo modelInfo = modelInfoService.getOne(queryWrapper);
        modelInfo.setTestDeviation(testDeviation);
        modelInfo.setScore(score);
        modelInfoService.updateById(modelInfo);
    }

    // 计算日增长率
    private void setIncreaseRate(List<StockPrices> stockPrices) {
        for (int i = 1; i < stockPrices.size(); i++) {
            double curPrice = stockPrices.get(i).getPrice1();
            double prePrice = stockPrices.get(i - 1).getPrice1();
            double increaseRate = prePrice == 0 ? 0 : (curPrice - prePrice) / prePrice;
            stockPrices.get(i).setIncreaseRate(increaseRate);
        }
    }

    // 判断两个数符号是否相同
    private boolean hasMistake(double num1, double num2) {
        if (num1 > 0) {
            return !(num2 > 0);
        } else {
            return !(num2 < 0);
        }
    }
}
