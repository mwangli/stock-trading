package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
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
import java.util.Set;
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

    @SneakyThrows
    @Override
    void run() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(StockInfo::getPrice);
        final List<StockInfo> list = stockInfoService.list(queryWrapper);
        Set<String> modelCode = redisTemplate.keys("model:model_**");
        for (StockInfo s : list) {
            if (!DateUtils.isWeekends(new Date()) && DateUtils.inTradingTimes1()) break;
            if (modelCode != null && modelCode.stream().anyMatch(c -> c.contains(s.getCode()))) continue;
            String stockCode = s.getCode();
            final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            log.info("股票[{}-{}],训练数据集大小为:{}", s.getName(), s.getCode(), stockHistoryPrices.size());
            if (stockHistoryPrices.size() < 100) continue;
            List<StockPrices> stockTestPrices = modelService.modelTrain(stockHistoryPrices);
            redisTemplate.opsForValue().set("model:model_" + s.getCode(), s.getCode(), 30, TimeUnit.DAYS);
            final Query deleteQuery = new Query(Criteria.where("code").is(s.getCode()));
            List<Object> removed = mongoTemplate.findAllAndRemove(deleteQuery, TEST_COLLECTION_NAME);
            mongoTemplate.insert(stockTestPrices, TEST_COLLECTION_NAME);
            log.info("股票[{}-{}],清空{}条，写入{}条测试集数据", s.getName(), s.getCode(), removed.size(), stockTestPrices.size());
        }
    }
}
