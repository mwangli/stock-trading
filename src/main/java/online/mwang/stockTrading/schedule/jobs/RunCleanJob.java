package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.AccountInfoService;
import online.mwang.stockTrading.web.service.ModelInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunCleanJob extends BaseJob {

    private static final String VALIDATION_COLLECTION_NAME = "stockPredictPrice";
    private static final String TRAIN_COLLECTION_NAME = "stockHistoryPrice";
    private static final String TEST_COLLECTION_NAME = "stockTestPrice";
    private final AccountInfoService accountInfoService;
    private final StockInfoService stockInfoService;
    private final ModelInfoService modelInfoService;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run() {
        cleanAccountInfo();
        cleanPredictPrice();
        cleanHistoryPrice();
        cleanTestData();
        cleanStockInfo();
    }

    private void cleanAccountInfo() {
        // 移除AccountInfo中一半的历史数据
        final List<AccountInfo> list = accountInfoService.list();
        final List<AccountInfo> deleteList = list.stream().skip(list.size() >> 1).collect(Collectors.toList());
        accountInfoService.removeBatchByIds(deleteList);
        log.info("共清理{}条账户信息历史数据。", deleteList.size());
    }

    private void cleanStockInfo() {
        // 清除已经退市的股票信息
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getDeleted, "0");
        List<StockInfo> deleteList = stockInfoService.list(queryWrapper);
        stockInfoService.removeBatchByIds(deleteList);
        log.info("共清理{}条账户退市股票信息。", deleteList.size());
    }

    private void cleanTestData() {
        // 清除无效的测试集数据
        final Query query = new Query(new Criteria().orOperator(
                Criteria.where("date").isNull(),
                Criteria.where("code").isNull(),
                Criteria.where("price1").isNull()));
        final List<StockPrices> remove = mongoTemplate.findAllAndRemove(query, StockPrices.class, TEST_COLLECTION_NAME);
        log.info("共清理{}条测试集无效数据。", remove.size());
    }

    private void cleanPredictPrice() {
        // 移除MongoDB中前几个的历史数据的预测价格历史数据,只保留最新的三个月数据
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MONTH, -3);
        final Query query = new Query(Criteria.where("date").lte(DateUtils.dateFormat.format(calendar.getTime())));
        final List<StockPrices> remove = mongoTemplate.findAllAndRemove(query, StockPrices.class, VALIDATION_COLLECTION_NAME);
        log.info("共清理{}条价格预测历史数据。", remove.size());
    }

    private void cleanHistoryPrice() {
        // 找到历史数据中价格缺失的最早一条数据
        Query findQuery = new Query(Criteria.where("price2").isNull()).with(Sort.by(Sort.Direction.ASC, "date")).limit(1);
        StockPrices invalidData = mongoTemplate.findOne(findQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        // 删除指定日期往后的数据
        if (invalidData != null) {
            log.info("查找到最早的无效的价格数据为:{},开始清除往后的历史数据!", invalidData);
            String startDate = invalidData.getDate();
            final Query query = new Query(Criteria.where("date").gte(startDate));
            final List<StockPrices> remove = mongoTemplate.findAllAndRemove(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            log.info("共清理{}条价格历史数据。", remove.size());
        }
        // 删除code或date为空的无效数据
        Query deleteQuery = new Query(Criteria.where("code").isNull().orOperator(Criteria.where("date").isNull()));
        List<StockPrices> remove = mongoTemplate.findAllAndRemove(deleteQuery, StockPrices.class, TRAIN_COLLECTION_NAME);
        log.info("共清理{}条无效历史数据!", remove.size());
    }
}
