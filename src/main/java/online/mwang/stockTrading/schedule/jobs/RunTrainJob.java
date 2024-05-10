package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
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
        // 启动多线程同时训练充分利用CPU资源
        int cores = Runtime.getRuntime().availableProcessors();
        int threads = debug ? (cores >> 1) + 1 : 1;
        log.info("启动模型训练线程数量为：{}", threads);
        CountDownLatch countDownLatch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) new Thread(() -> train(countDownLatch)).start();
        countDownLatch.await();
    }

    @SneakyThrows
    private void train(CountDownLatch countDownLatch) {
        List<StockInfo> stockInfos = stockInfoService.list();
        for (StockInfo s : stockInfos) {
            try {
                if (isInterrupted) break;
                Boolean check = redisTemplate.opsForValue().setIfAbsent("model:code:" + s.getCode(), s.getCode(), 5, TimeUnit.MINUTES);
                if (check != null && !check) continue;
                final Query query = new Query(Criteria.where("code").is(s.getCode())).with(Sort.by(Sort.Direction.ASC, "date"));
                List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
                log.info("股票[{}-{}], 训练数据集大小为:{}", s.getCode(), s.getName(), stockHistoryPrices.size());
                List<StockPrices> stockTestPrices = modelService.modelTrain(stockHistoryPrices);
                if (CollectionUtils.isEmpty(stockTestPrices)) continue;
                final Query deleteQuery = new Query(Criteria.where("code").is(s.getCode()));
                List<Object> removed = mongoTemplate.findAllAndRemove(deleteQuery, TEST_COLLECTION_NAME);
                mongoTemplate.insert(stockTestPrices, TEST_COLLECTION_NAME);
                redisTemplate.opsForValue().set("model:code:" + s.getCode(), s.getCode(), 30, TimeUnit.DAYS);
                log.info("股票[{}-{}],清空{}条，写入{}条测试集数据", s.getCode(), s.getName(), removed.size(), stockTestPrices.size());
                // 更新模型评分
                updateModelScore(s.getCode());
            } catch (Exception e) {
                e.printStackTrace();
                log.info("当前股票[{}-{}]模型训练异常!", s.getCode(), s.getName());
            }
        }
        countDownLatch.countDown();
    }

    public void updateModelScore(String stockCode) {
        // 计算测试误差
        double testDeviation = calculateDeviation(stockCode);
        double score = (1 - testDeviation) * 100;
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModelInfo::getCode, stockCode);
        ModelInfo modelInfo = modelInfoService.getOne(queryWrapper);
        modelInfo.setTestDeviation(testDeviation);
        modelInfo.setScore(score);
        modelInfoService.updateById(modelInfo);
    }

    private double calculateDeviation(String stockCode) {
        // 获取测试集数据
        List<StockPrices> pricesList = mongoTemplate.find(new Query(Criteria.where("code").is(stockCode)), StockPrices.class, TEST_COLLECTION_NAME);
        if (CollectionUtils.isEmpty(pricesList)) return 1;
        String maxDate = pricesList.stream().map(StockPrices::getDate).max(String::compareTo).orElse("");
        String minDate = pricesList.stream().map(StockPrices::getDate).min(String::compareTo).orElse("");
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate));
        List<StockPrices> historyPrices = mongoTemplate.find(historyQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        setIncreaseRate(historyPrices);
        setIncreaseRate(pricesList);
        // 计算测试集误差和评分
        int mistakeCount = 0;
        for (int i = 1; i < pricesList.size(); i++) {
            Double testIncrease = pricesList.get(i).getIncreaseRate();
            Double actualIncrease = historyPrices.get(i).getIncreaseRate();
            if (hasMistake(testIncrease, actualIncrease)) mistakeCount++;
        }
        return (double) mistakeCount / pricesList.size();
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
            return num2 <= 0;
        } else {
            return (num2 > 0);
        }
    }
}
