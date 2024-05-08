package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
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
import java.util.concurrent.TimeUnit;

/**
 * @author 13255
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunTrainJob extends BaseJob {

    private static final String TEST_COLLECTION_NAME = "stockTestPrice";
    private static final String VALIDATE_COLLECTION_NAME = "stockPredictPrice";
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
            if (redisTemplate.opsForValue().get("model:code:" + s.getCode()) != null) continue;
            redisTemplate.opsForValue().set("model:code:" + s.getCode(), s.getCode(), 10, TimeUnit.MINUTES);
            String stockCode = s.getCode();
            String stockName = s.getName();
            final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            if (stockHistoryPrices.size() < 100) continue;
            log.info("股票[{}-{}], 训练数据集大小为:{}", stockName, stockCode, stockHistoryPrices.size());
            List<StockPrices> stockTestPrices = modelService.modelTrain(stockHistoryPrices);
            if (CollectionUtils.isEmpty(stockTestPrices)) continue;
            final Query deleteQuery = new Query(Criteria.where("code").is(stockCode));
            List<Object> removed = mongoTemplate.findAllAndRemove(deleteQuery, TEST_COLLECTION_NAME);
            mongoTemplate.insert(stockTestPrices, TEST_COLLECTION_NAME);
            redisTemplate.opsForValue().set("model:code:" + s.getCode(), s.getCode(), 30, TimeUnit.DAYS);
            log.info("股票[{}-{}],清空{}条，写入{}条测试集数据", stockName, stockCode, removed.size(), stockTestPrices.size());
            // 更新模型评分
            modelInfoService.updateModelScore(stockCode);
        }
    }
}
