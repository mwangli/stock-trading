package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.mapper.OrderInfoMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunOrderJob extends BaseJob {

    private final IDataService dataService;
    private final OrderInfoService orderInfoService;
    private final OrderInfoMapper orderInfoMapper;

    @SneakyThrows
    @Override
    public void run() {
        // 同步今日成交订单数据 (由于买入卖出操作会进行很多尝试操作，所有会存在很多撤销的订单，这些数据我们并不需要)
        List<OrderInfo> todayOrders = dataService.getTodayOrder();
        todayOrders.forEach(this::fixProps);
        orderInfoService.saveBatch(todayOrders);
        log.info("共写入{}条订单交易记录", todayOrders.size());
    }

    private void fixProps(OrderInfo orderInfo) {
        Date date = new Date();
        orderInfo.setCreateTime(date);
        orderInfo.setUpdateTime(date);
    }
}
