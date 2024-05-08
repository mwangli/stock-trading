package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
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

    private final IStockService dataService;
    private final OrderInfoService orderInfoService;

    @SneakyThrows
    @Override
    public void run() {
        // 同步今日成交订单数据
        List<OrderInfo> todayOrders = dataService.getTodayOrder();
        todayOrders.forEach(this::fixProps);
        List<OrderInfo> orderInfos = orderInfoService.list();
        orderInfos.forEach(orderInfo -> {
            OrderInfo find = orderInfoService.getOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getAnswerNo, orderInfo.getAnswerNo()));
            if (find != null) {
                log.info("当前订单{}已经存在，无需写入!", orderInfo);
            } else {
                orderInfoService.save(orderInfo);
                log.info("写入{}条交易订单", orderInfo);
            }
        });
    }

    private void fixProps(OrderInfo orderInfo) {
        Date date = new Date();
        orderInfo.setCreateTime(date);
        orderInfo.setUpdateTime(date);
    }
}
