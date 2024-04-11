package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
public class RunHistoryJob extends BaseJob {

    private final IDataService dataService;
    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run() {
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getDeleted, 1);
        List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        log.info("共需更新{}支股票最新历史价格数据", stockInfoList.size());
        stockInfoList.forEach(this::writeHistoryPriceDataToMongo);
    }

    @SneakyThrows
    public void writeHistoryPriceDataToMongo(StockInfo stockInfo) {
        List<DailyItem> historyPrices = dataService.getHistoryPrices(stockInfo.getCode());
        List<StockHistoryPrice> stockHistoryPriceList = historyPrices.stream().map(item -> {
            StockHistoryPrice stockHistoryPrice = new StockHistoryPrice();
            stockHistoryPrice.setName(stockInfo.getName());
            stockHistoryPrice.setCode(stockInfo.getCode());
            stockHistoryPrice.setDate(item.getDate());
            stockHistoryPrice.setPrice1(item.getPrice1());
            stockHistoryPrice.setPrice1(item.getPrice2());
            stockHistoryPrice.setPrice1(item.getPrice3());
            stockHistoryPrice.setPrice1(item.getPrice4());
            return stockHistoryPrice;
        }).collect(Collectors.toList());
        // 翻转一下，将日期从新到旧排列，这样读到已经存在的数据，就可以跳过后续判断写入逻辑
        Collections.reverse(stockHistoryPriceList);
        for (StockHistoryPrice s : stockHistoryPriceList) {
            // 先查询是否已经存在相同记录
            Query query = new Query(Criteria.where("date").is(s.getDate()).and("code").is(s.getCode()));
//            // 每只股票写入不同的表
            StockHistoryPrice one = mongoTemplate.findOne(query, StockHistoryPrice.class);
            if (Objects.isNull(one)) {
                mongoTemplate.save(s);
                log.info("当前股票{}-{}-{}，历史数据写入完成", s.getName(), s.getCode(), s.getDate());
            } else {
                if (one.getPrice3() == null || one.getPrice4() == null) {
                    log.info("当前股票{}-{}-{}，历史数据不完整进行修改操作", s.getName(), s.getCode(), s.getDate());
                    Update update = new Update().set("price3", one.getPrice3()).set("price4", one.getPrice3());
                    mongoTemplate.updateFirst(query, update, StockHistoryPrice.class);
                } else {
                    // 数据完整，则跳过后续处理
                    break;
                }
            }
        }
        log.info("股票:{}-{}, 所有历史价格数据保存完成！", stockInfo.getName(), stockInfo.getCode());
    }
}
