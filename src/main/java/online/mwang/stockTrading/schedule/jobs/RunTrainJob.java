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
        queryWrapper.isNotNull(StockInfo::getCode);
        queryWrapper.orderByDesc(StockInfo::getPrice);
        final List<StockInfo> list = stockInfoService.list(queryWrapper);
        log.info("共获取{}条待训练股票.", list.size());
        for (StockInfo s : list) {
            String stockCode = s.getCode();
            String stockName = s.getName();
            String lastUpdateTime = (String) redisTemplate.opsForHash().get("model:" + stockCode, "lastUpdateTime");
            if (lastUpdateTime != null && DateUtils.diff(DateUtils.dateFormat.parse(lastUpdateTime), new Date(), true) < 30) {
                log.info("当前股票[{}-{}]，最近30天内已经训练过模型了，跳过训练", stockName, stockCode);
                continue;
            }
            log.info("股票[{}-{}],模型训练开始...", s.getName(), s.getCode());
            long start = System.currentTimeMillis();
            final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            log.info("股票[{}-{}],训练数据集大小为:{}", s.getName(), s.getCode(), stockHistoryPrices.size());
            if (stockHistoryPrices.size() == 0) {
                log.info("当前股票[{}-{}]，未获取到训练数据集，跳过训练！", stockName, stockCode);
                continue;
            }
            List<StockPrices> stockTestPrices = modelService.modelTrain(stockHistoryPrices);
            final Query deleteQuery = new Query(Criteria.where("code").is(s.getCode()));
            final List<StockPrices> remove = mongoTemplate.findAllAndRemove(deleteQuery, TEST_COLLECTION_NAME);
            log.info("股票[{}-{}],清除{}条废弃测试集数据", s.getName(), stockCode, remove.size());
            mongoTemplate.insert(stockTestPrices, TEST_COLLECTION_NAME);
            log.info("股票[{}-{}],新写入{}条测试集数据", s.getName(), stockCode, stockTestPrices.size());
            long end = System.currentTimeMillis();
            log.info("股票[{}-{}],模型训练完成，共耗时:{}", s.getName(), stockCode, DateUtils.timeConvertor(end - start));
        }
    }
}
