package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

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
public class RunInitialJob extends BaseJob {

    private final IDataService dataService;
    private final AccountInfoMapper accountInfoMapper;
    private final IModelService modelService;
    private final MongoTemplate mongoTemplate;
    private final StockInfoService stockInfoService;

    @Override
    public void run() {
        // 首次初始化执行，写入4000支股票，每只股票约500条数据
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getDeleted, 1);
        List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        log.info("共需写入{}支股票历史数据", stockInfoList.size());
        stockInfoList.forEach(s -> {
            List<DailyItem> historyPrices = dataService.getHistoryPrices(s.getCode());
            List<StockHistoryPrice> stockHistoryPrices = historyPrices.stream().map(item -> {
                StockHistoryPrice stockHistoryPrice = new StockHistoryPrice();
                stockHistoryPrice.setName(s.getName());
                stockHistoryPrice.setCode(s.getCode());
                stockHistoryPrice.setDate(item.getDate());
                stockHistoryPrice.setPrice1(item.getPrice1());
                stockHistoryPrice.setPrice2(item.getPrice2());
                stockHistoryPrice.setPrice3(item.getPrice3());
                stockHistoryPrice.setPrice4(item.getPrice4());
                return stockHistoryPrice;
            }).collect(Collectors.toList());
            String collectionName = "code_" + s.getCode();
            // 先判断是否有数据存在，防止误操作写入重复数据
            if (mongoTemplate.count(new Query(), collectionName) == 0) {
                mongoTemplate.insert(stockHistoryPrices, collectionName);
                log.info("股票[{}-{}]，历史数据写入完成！", s.getName(), s.getCode());
            }
        });
    }
}
