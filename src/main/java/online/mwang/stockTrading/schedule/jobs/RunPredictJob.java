package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.ModelInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob extends BaseJob {

    private final IPredictService modelService;
    private final StockInfoService stockInfoService;
    private final ModelInfoService modelInfoService;

    @SneakyThrows
    @Override
    void run() {
        // 获取股票信息和模型信息
        List<StockInfo> stockInfos = stockInfoService.list();
        List<ModelInfo> modelInfos = modelInfoService.list();
        String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
        CountDownLatch countDownLatch = new CountDownLatch(stockInfos.size());
        for (StockInfo stockInfo : stockInfos) {
            ModelInfo modelInfo = modelInfos.stream().filter(m -> m.getCode().equals(stockInfo.getCode())).findFirst().orElse(new ModelInfo());
            fixedThreadPool.submit(() -> predict(stockInfo, date, modelInfo, countDownLatch));
        }
        countDownLatch.await();
        log.info("所有股票价格预测完成!");
    }

    void predict(StockInfo stockInfo, String date, ModelInfo modelInfo, CountDownLatch countDownLatch) {
        try {
            Query query = new Query(Criteria.where("code").is(stockInfo.getCode())).with(Sort.by(Sort.Direction.DESC, "date")).limit(EXAMPLE_LENGTH);
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            if (stockHistoryPrices.size() != EXAMPLE_LENGTH) throw new BusinessException("预测输入数据异常!");
            Collections.reverse(stockHistoryPrices);
            // 预填充最后一个预测数据以方便构造输入数据集
            stockHistoryPrices.add(new StockPrices(0.0));
            StockPrices predictPrice = modelService.modelPredict(stockHistoryPrices);
            if (predictPrice == null) throw new BusinessException("价格预测数据异常!");
            predictPrice.setDate(date);
            log.info("当前股票[{}-{}],{}预测价格为:{}", predictPrice.getCode(), predictPrice.getName(), date, predictPrice.getPrice1());
            mongoTemplate.remove(new Query(Criteria.where("date").is(date).and("code").is(stockInfo.getCode())), VALIDATION_COLLECTION_NAME);
            mongoTemplate.insert(predictPrice, VALIDATION_COLLECTION_NAME);
            updateStockScore(stockInfo, modelInfo);
        } catch (Exception e) {
            e.printStackTrace();
            log.info("当前股票价格预测异常：{}", e.getMessage());
        } finally {
            countDownLatch.countDown();
        }
    }

    private void updateStockScore(StockInfo stockInfo, ModelInfo modelInfo) {
        // 获取预测结果集中的最后10条数据
        Query query = new Query(Criteria.where("code").is(stockInfo.getCode())).with(Sort.by(Sort.Direction.DESC, "date")).limit(10);
        List<StockPrices> predictPriceList = mongoTemplate.find(query, StockPrices.class, VALIDATION_COLLECTION_NAME);
        // 判断最近10次的最大连续增长次数和总增长次数，连续增长次数越多，则说明收益越稳定
        int totalIncreaseCount = 0, continuousIncreaseCount = 0, maxIncreaseCount = 0;
        for (int i = 0; i < predictPriceList.size() - 1; i++) {
            Double curPrice = predictPriceList.get(i).getPrice1();
            Double prePrice = predictPriceList.get(i + 1).getPrice1();
            if (curPrice > prePrice) {
                totalIncreaseCount++;
                continuousIncreaseCount++;
                maxIncreaseCount = Math.max(maxIncreaseCount, continuousIncreaseCount);
            } else {
                continuousIncreaseCount = 0;
            }
        }
        double score = (double) totalIncreaseCount * 10 + maxIncreaseCount * 10;
        // 将价格预测评分，和模型准确率评分的加权和作为最终评分
        Double finalScore = score * 0.8 + modelInfo.getScore() * 0.2;
        stockInfo.setScore(finalScore);
        stockInfoService.updateById(stockInfo);
    }
}

