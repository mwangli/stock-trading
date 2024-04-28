package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IPredictService;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.bean.po.StockInfo;
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

    private final IPredictService modelService;
    private final MongoTemplate mongoTemplate;
    private final StockInfoService stockInfoService;
    private final StringRedisTemplate redisTemplate;
    private static final String TEST_COLLECTION_NAME = "stockTestPrices";
    private static final String TRAIN_COLLECTION_NAME = "stockHistoryPrices";


    @SneakyThrows
    @Override
    void run() {
        final LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(StockInfo::getPrice);
        final List<StockInfo> list = stockInfoService.list(queryWrapper);
        log.info("共获取{}条待训练股票.", list.size());
        for (StockInfo s : list) {
            String lastUpdateTime = (String) redisTemplate.opsForHash().get("model:" + s.getCode(), "lastUpdateTime");
            if (lastUpdateTime != null && DateUtils.diff(new Date(), DateUtils.dateTimeFormat.parse(lastUpdateTime), true) < 30) {
                log.info("股票[{}-{}],近30天内已经训练过模型了,跳过训练...", s.getName(), s.getCode());
                continue;
            }
            log.info("股票[{}-{}],模型训练开始...", s.getName(), s.getCode());
            long start = System.currentTimeMillis();
            String stockCode = s.getCode();
            final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
            List<StockPrices> stockHistoryPrices = mongoTemplate.find(query, StockPrices.class,TRAIN_COLLECTION_NAME);
            log.info("股票[{}-{}],训练数据集大小为:{}", s.getName(), s.getCode(), stockHistoryPrices.size());
            if (stockHistoryPrices.size() == 0) {
                log.info("未获取到训练数据集，跳过训练！");
                continue;
            }
            List<StockPrices> stockTestPrices = modelService.modelTrain(stockHistoryPrices);
            final Query deleteQuery = new Query(Criteria.where("code").is(s.getCode()));
            final List<StockPrices> remove = mongoTemplate.findAllAndRemove(deleteQuery,TEST_COLLECTION_NAME);
            log.info("股票[{}-{}],清除{}条废弃测试集数据", s.getName(), stockCode, remove.size());
            mongoTemplate.insert(stockTestPrices, TEST_COLLECTION_NAME);
            log.info("股票[{}-{}],新写入{}条测试集数据", s.getName(), stockCode, stockTestPrices.size());
            long end = System.currentTimeMillis();
            log.info("股票[{}-{}],模型训练完成，共耗时:{}", s.getName(), stockCode, DateUtils.timeConvertor(end - start));
            redisTemplate.opsForHash().put("model:" + s.getCode(), "lastUpdateTime", DateUtils.dateTimeFormat.format(new Date()));
        }
    }
}
