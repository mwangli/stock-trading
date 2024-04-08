package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.stereotype.Component;

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

    private final IDataService dataService;
    private final StockInfoService stockInfoService;

    @Override
    public void run() {
        log.info("请尝试以更好的方案来更新交易权限....");
        // 尝试以更好的方案来更新交易权限，而不是试错
//        List<String> errorCodes = Arrays.asList("[251112]", "[251127]", "[251299]", "该股票是退市");
//        List<StockInfo> stockInfos = stockInfoService.list();
//        final HashSet<String> set = new HashSet<>();
//        List<StockInfo> stockInfoList = stockInfos.stream().peek(info -> {
//            String res = dataService.buySale("B", info.getCode(), 100.0, 100.0);
//            final String message = res.getString("ERRORMESSAGE");
//            set.add(message);
//            if (errorCodes.stream().anyMatch(message::startsWith)) {
//                info.setPermission("0");
//            } else {
//                info.setPermission("1");
//            }
//        }).collect(Collectors.toList());
//        stockInfoService.updateBatchById(stockInfoList);
//        log.info("交易权限错误信息合集:{}", set);
//        stockInfoService.updateBatchById(stockInfos);
//         取消所有提交的订单
//        dataService.cancelAllOrder();
    }
}
