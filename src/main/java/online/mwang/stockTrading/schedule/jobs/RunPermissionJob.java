package online.mwang.stockTrading.schedule.jobs;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
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
public class RunPermissionJob extends BaseJob {

    private final IStockService dataService;
    private final StockInfoService stockInfoService;

    @Override
    public void run() {
        log.info("请尝试以更好的方案来更新交易权限....");
        //   尝试以更好的方案来更新交易权限，而不是试错
        List<String> errorCodes = Arrays.asList("[251112]", "[251127]", "[251299]", "该股票是退市");
        List<StockInfo> stockInfos = stockInfoService.list();
        final HashSet<String> set = new HashSet<>();
        stockInfos.forEach(info -> {
            JSONObject result = dataService.buySale("B", info.getCode(), 100.0, 100.0);
            final String message = result.getString("ERRORMESSAGE");
            set.add(message);
            if (errorCodes.stream().anyMatch(message::startsWith)) {
                info.setPermission("0");
            } else {
                info.setPermission("1");
            }
        });
        stockInfoService.updateBatchById(stockInfos);
        log.info("交易权限错误信息合集:{}", set);
        // 取消所有提交的订单
        dataService.cancelAllOrder();
    }
}
