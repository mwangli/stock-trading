package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.service.OrderInfoService;
import org.springframework.stereotype.Component;

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
public class RunOrderJob extends BaseJob {

    private final IStockService stockService;
    private final OrderInfoService orderInfoService;

    @SneakyThrows
    @Override
    public void run() {
        // 同步今日成交订单数据
        List<OrderInfo> todayOrders = stockService.getTodayOrder();
        todayOrders.forEach(this::fixProps);
        List<OrderInfo> orderInfos = orderInfoService.list();
        List<OrderInfo> saveList = orderInfos.stream().filter(o -> orderInfos.stream().noneMatch(i -> i.getAnswerNo().equals(o.getAnswerNo()))).collect(Collectors.toList());
        todayOrders.forEach(order -> {
            if (orderInfos.stream().anyMatch(o -> o.getAnswerNo().equals(order.getAnswerNo()))) {
                log.info("当前股票[{}-{}]，交易订单已经存在，无需写入！", order.getCode(), order.getName());
            } else {
                orderInfoService.save(order);
            }
        });
    }

    private void fixProps(OrderInfo orderInfo) {
        Date date = new Date();
        orderInfo.setCreateTime(date);
        orderInfo.setUpdateTime(date);
    }
}
