package online.mwang.stockTrading.schedule.jobs;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.data.IDataService;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.mapper.OrderInfoMapper;
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

    private final IDataService dataService;
    private final OrderInfoService orderInfoService;
    private final OrderInfoMapper orderInfoMapper;

    @SneakyThrows
    @Override
    public void run() {
        // 同步历史订单数据 (历史订单数据中没有订单状态)
        List<OrderInfo> lastOrders = dataService.getHistoryOrder();
        List<OrderInfo> todayOrders = dataService.getTodayOrder();
        lastOrders.addAll(todayOrders);
        // 获取已有订单编号，做差集写入
        List<String> answerNo = orderInfoMapper.listAnswerNo();
        List<OrderInfo> insertList = todayOrders.stream().filter(o -> !answerNo.contains(o.getAnswerNo())).collect(Collectors.toList());
        insertList.forEach(this::fixProps);
        orderInfoService.saveBatch(insertList);
        log.info("共同步{}条订单交易记录", insertList.size());
    }

    private void fixProps(OrderInfo orderInfo) {
        Date date = new Date();
        orderInfo.setCreateTime(date);
        orderInfo.setUpdateTime(date);
    }
}
