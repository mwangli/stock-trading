package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunHistoryJob extends BaseJob {

    private final IStockService stockService;
    private final StockInfoService stockInfoService;

    @SneakyThrows
    @Override
    public void run() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        log.info("共需更新{}支股票最新历史价格数据", stockInfoList.size());
        CountDownLatch countDownLatch = new CountDownLatch(stockInfoList.size());
        stockInfoList.forEach(s -> fixedThreadPool.submit(() -> writeHistoryPriceDataToMongo(s,countDownLatch)));
        countDownLatch.await();
        log.info("所有历史数据写入完成");
    }

    @SneakyThrows
    public void writeHistoryPriceDataToMongo(StockInfo stockInfo,CountDownLatch countDownLatch) {
        List<StockPrices> historyPrices = stockService.getHistoryPrices(stockInfo.getCode());
        historyPrices.forEach(s -> s.setName(stockInfo.getName()));
        // 翻转一下，将日期从新到旧排列，这样读到已经存在的数据，就可以跳过后续判断写入逻辑
        Collections.reverse(historyPrices);
        for (StockPrices s : historyPrices) {
            // 先查询是否已经存在相同记录
            Query query = new Query(Criteria.where("date").is(s.getDate()).and("code").is(s.getCode()));
            StockPrices one = mongoTemplate.findOne(query, StockPrices.class, TRAIN_COLLECTION_NAME);
            if (Objects.isNull(one)) {
                mongoTemplate.insert(s, TRAIN_COLLECTION_NAME);
                log.info("当前股票{}-{}-{}，写入历史数据", s.getName(), s.getCode(), s.getDate());
            } else {
                if (one.getPrice3() == null || one.getPrice4() == null) {
                    log.info("当前股票{}-{}-{}，历史数据不完整进行修改操作", s.getName(), s.getCode(), s.getDate());
                    Update update = new Update().set("price3", one.getPrice3()).set("price4", one.getPrice3());
                    mongoTemplate.updateFirst(query, update, StockPrices.class, TRAIN_COLLECTION_NAME);
                } else {
                    // 数据完整，则跳过后续数据处理
                    break;
                }
            }
        }
        log.info("股票:{}-{}, 所有历史价格数据保存完成！", stockInfo.getName(), stockInfo.getCode());
        countDownLatch.countDown();
    }
}
