package online.mwang.stockTrading.web.job;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.mapper.StockInfoMapper;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunFlushJob extends BaseJob {

    private final AllJobs jobs;
    private final StockInfoService stockInfoService;
    private final StockInfoMapper stockInfoMapper;

    @Override
    public void run(String runningId) {
        log.info("更新权限任务执行开始====================================");
        flushPermission();
        log.info("更新权限任务执行结束====================================");
    }

    @SneakyThrows
    public void flushPermission() {
        // 此处不能使用多线程处理,因为每次请求会使上一个Token失效
        List<String> errorCodes = Arrays.asList("[251112]", "[251127]", "[251299]", "该股票是退市");
        List<StockInfo> stockInfos = stockInfoService.list();
        final HashSet<String> set = new HashSet<>();
        AtomicInteger count = new AtomicInteger();
        stockInfos.forEach(info -> {
            JSONObject res = jobs.buySale("B", info.getCode(), 100.0, 100.0);
            final String message = res.getString("ERRORMESSAGE");
            set.add(message);
            if (errorCodes.stream().anyMatch(message::startsWith)) {
                info.setPermission("0");
            } else {
                info.setPermission("1");
            }
            stockInfoMapper.updateById(info);
            if (count.incrementAndGet() % 100 == 0) {
                log.info("已更新{}条股票交易权限记录。", count.get());
            }
        });
        log.info("交易权限错误信息合集:{}", set);
        // 取消所有提交的订单
        jobs. cancelAllOrder();
    }
}
