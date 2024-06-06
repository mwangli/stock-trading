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
        log.info("获取到今日已成交订单：{}", todayOrders);
        List<OrderInfo> orderInfos = orderInfoService.list();
        List<OrderInfo> saveList = orderInfos.stream().filter(o -> orderInfos.stream().noneMatch(i -> i.getAnswerNo().equals(o.getAnswerNo()))).collect(Collectors.toList());
        todayOrders.forEach(order -> {
            if (orderInfos.stream().noneMatch(o -> o.getAnswerNo().equals(order.getAnswerNo()))) {
                fixProps(order);
                orderInfoService.save(order);
                log.info("成功保存订单：{}", order);
            } else {
                log.info("当前股票[{}-{}]，交易订单已经存在，无需写入！", order.getCode(), order.getName());
            }
        });
    }

    private void fixProps(OrderInfo orderInfo) {
        Date date = new Date();
        orderInfo.setCreateTime(date);
        orderInfo.setUpdateTime(date);
    }
}
