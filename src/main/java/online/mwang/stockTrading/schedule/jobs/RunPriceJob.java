package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: RunPriceJob
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunPriceJob extends BaseJob {

    private final IDataService dataService;
    private final StockInfoService stockInfoService;
    private final StringRedisTemplate redisTemplate;
    private final String NEW_PRICE_KEY = "NEW_PRICE_KEY";

    @Override
    public void run() {
        // 获取每只股票最新的当前价格写入到redis map
        List<StockInfo> newInfos = dataService.getDataList();
        newInfos.forEach(s -> redisTemplate.opsForHash().put(NEW_PRICE_KEY, s.getCode(), s.getPrice().toString()));
        List<StockInfo> list = stockInfoService.list().stream().peek(stockInfo -> {
            // 仅修改最新价格数据
            double newPrice = newInfos.stream().filter(info -> info.getCode().equals(stockInfo.getCode())).mapToDouble(StockInfo::getPrice).findFirst().orElse(0.0);
            stockInfo.setPrice(newPrice);
            stockInfo.setUpdateTime(new Date());
        }).collect(Collectors.toList());
        stockInfoService.updateBatchById(list);
    }
}
