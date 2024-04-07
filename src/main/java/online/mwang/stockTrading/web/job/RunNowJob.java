package online.mwang.stockTrading.web.job;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class RunNowJob extends BaseJob {

    private final AllJobs jobs;
    private final StockInfoService stockInfoService;

    @Override
    public void run(String runningId) {
        log.info("更新股票实时价格任务执行开始====================================");
        updateNowPrice();
        log.info("更新股票实时价格任务执行结束====================================");
    }

    @SneakyThrows
    public void updateNowPrice() {
        List<StockInfo> newInfos = jobs.getDataList();
        final List<StockInfo> dataList = stockInfoService.list();
        final ArrayList<StockInfo> saveList = new ArrayList<>();
//        final StrategyParams params = getStrategyParams();
        newInfos.forEach(newInfo -> {
            AtomicBoolean exist = new AtomicBoolean(false);
            dataList.stream().filter(s -> s.getCode().equals(newInfo.getCode())).findFirst().ifPresent(p -> {
                Double nowPrice = newInfo.getPrice();
//                List<DailyItem> priceList = JSON.parseArray(p.getPrices(), DailyItem.class);
//                List<DailyItem> rateList = JSON.parseArray(p.getIncreaseRate(), DailyItem.class);
//                Double score = handleScore(nowPrice, priceList, rateList, params);
//                Double score = handleScore(p);
//                p.setScore(score);
                p.setPrice(newInfo.getPrice());
                p.setIncrease(newInfo.getIncrease());
                p.setUpdateTime(new Date());
                p.setDeleted("1");
                saveList.add(p);
                exist.set(true);
                dataList.remove(p);
            });
            if (!exist.get()) {
                Date now = new Date();
                newInfo.setCreateTime(now);
                newInfo.setUpdateTime(now);
                newInfo.setPermission("1");
                newInfo.setBuySaleCount(0);
                newInfo.setScore(0.0);
                newInfo.setPrices("[]");
                newInfo.setIncreaseRate("[]");
                newInfo.setDeleted("1");
                saveList.add(newInfo);
                log.info("获取到新上市股票:{}", newInfo);
            }
        });
        // 标记退市股票
        if (CollectionUtils.isNotEmpty(dataList)) {
            log.info("标记退市股票:{}", dataList.stream().map(s -> s.getCode().concat("-").concat(s.getName())).collect(Collectors.toList()));
            dataList.forEach(d -> d.setDeleted("0"));
            saveList.addAll(dataList);
        }
        jobs.saveDate(saveList);
    }

}
