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
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunPredictJob extends BaseJob {

    public static final int EXAMPLE_LENGTH = 22;

    private final IPredictService modelService;
    private final StockInfoService stockInfoService;
    private final ModelInfoService modelInfoService;

    @SneakyThrows
    @Override
    void run() {
        // 获取股票信息和模型信息
        List<StockInfo> stockInfos = stockInfoService.list();
        String date = DateUtils.dateFormat.format(DateUtils.getNextTradingDay(new Date()));
        CountDownLatch countDownLatch = new CountDownLatch(stockInfos.size());
        log.info("共提交{}条股票价格预测任务", stockInfos.size());
        for (StockInfo stockInfo : stockInfos) {
            fixedThreadPool.submit(() -> predict(stockInfo, date, countDownLatch));
        }
        countDownLatch.await();
        log.info("所有股票价格预测完成!");
    }

    void predict(StockInfo stockInfo, String date, CountDownLatch countDownLatch) {
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
            updateStockScore(stockInfo);
        } catch (Exception e) {
            log.info("当前股票[{}-{}],价格预测异常：{}", stockInfo.getName(), stockInfo.getCode(), e.getMessage());
        } finally {
            countDownLatch.countDown();
            log.info("countDownLatch:{}", countDownLatch.getCount());
            ThreadPoolExecutor executor = (ThreadPoolExecutor) fixedThreadPool;
            log.info("剩余任务数量：{}", executor.getQueue().size());
        }
    }

    private void updateStockScore(StockInfo stockInfo) {
        // 获取预测结果集中的最后2条数据
        Query query = new Query(Criteria.where("code").is(stockInfo.getCode())).with(Sort.by(Sort.Direction.DESC, "date")).limit(2);
        List<StockPrices> predictPriceList = mongoTemplate.find(query, StockPrices.class, VALIDATION_COLLECTION_NAME);
        double score1 = 0;
        if (predictPriceList.size() >= 2) {
            StockPrices lastPrice = predictPriceList.get(0);
            StockPrices prePrice = predictPriceList.get(1);
            double increaseRate = (lastPrice.getPrice1() - prePrice.getPrice1()) / prePrice.getPrice1();
            score1 = increaseRate * 100 * 10;
        }
        // 将价格预测评分，和模型准确率评分的加权和作为最终评分
        double score2 = 0;
        LambdaQueryWrapper<ModelInfo> queryWrapper = new LambdaQueryWrapper<ModelInfo>().eq(ModelInfo::getCode, stockInfo.getCode());
        ModelInfo modelInfo = modelInfoService.getOne(queryWrapper);
        if (modelInfo != null) {
            score2 = modelInfo.getScore();
        }
        double finalScore = score1 * 0.5 + score2 * 0.5;
        stockInfo.setScore(finalScore);
        stockInfoService.updateById(stockInfo);
    }
}

