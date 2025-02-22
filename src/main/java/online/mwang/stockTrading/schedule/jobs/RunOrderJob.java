package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.stereotype.Component;

import java.util.*;
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
    private final TradingRecordService tradingRecordService;

    @SneakyThrows
    @Override
    public void run() {
        initHistoryOrder();
    }

    private void initHistoryOrder() {
        // 初始化订单数据，当交易记录数据丢失，或者在证券平台上已有订单数据，需要同步
        // 将数据写入到TradingRecord 和 OrderInfo表
        final List<OrderInfo> historyOrders = stockService.getHistoryOrder();
        final List<OrderInfo> orderInfoList = stockService.getTodayOrder();
        historyOrders.addAll(orderInfoList);
        List<OrderInfo> distinctOrders = historyOrders.stream().distinct().collect(Collectors.toList());
        // 写入订单信息
        Set<String> answerNoSet = orderInfoService.list().stream().map(OrderInfo::getAnswerNo).collect(Collectors.toSet());
        List<OrderInfo> saveOrderInfos = distinctOrders.stream().filter(o -> !answerNoSet.contains(o.getAnswerNo())).collect(Collectors.toList());
        saveOrderInfos.forEach(this::fixOrderProps);
        orderInfoService.saveBatch(saveOrderInfos);
        log.info("共写入{}条订单记录！", saveOrderInfos.size());
        // 写入交易记录
        final List<TradingRecord> tradingRecords = buildTradingRecord(distinctOrders);
        final List<TradingRecord> saveRecordList = tradingRecords.stream().filter(r -> !isExistTradingRecord(r)).collect(Collectors.toList());
        tradingRecords.forEach(this::fixRecordProps);
        tradingRecordService.saveBatch(saveRecordList);
        log.info("共写入{}条交易记录！", saveRecordList.size());
    }

    private boolean isExistTradingRecord(TradingRecord record) {
        final LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TradingRecord::getCode, record.getCode());
        queryWrapper.eq(TradingRecord::getBuyNo, record.getBuyNo());
        final List<TradingRecord> findList = tradingRecordService.list(queryWrapper);
        return findList.size() > 0;
    }

    private void fixOrderProps(OrderInfo orderInfo) {
        orderInfo.setStatus("1");
        double amount = orderInfo.getNumber() * orderInfo.getPrice();
        Double peer = stockService.getPeeAmount(amount);
        orderInfo.setPeer(peer);
        String type = orderInfo.getType();
        orderInfo.setAmount("卖出".equals(type) ? amount - peer : amount + peer);
        orderInfo.setCreateTime(new Date());
        orderInfo.setUpdateTime(new Date());
    }

    private void fixRecordProps(TradingRecord tradingRecord) {
        tradingRecord.setCreateTime(new Date());
        tradingRecord.setUpdateTime(new Date());
    }

    @SneakyThrows
    private List<TradingRecord> buildTradingRecord(List<OrderInfo> historyOrder) {
        // 将订单数据组装成交易数据，这个问题难点在于可能存在同一个股票的多笔交易订单
        // 可能买入一笔，然后分三笔卖出，如何将这4个订单数据组装成同一个交易记录中，后续可能还买入同一股
        // 使用HashMap来实现，维护一个不完整记录Map，出现卖出订单时，尝试找买入记录进行填充(卖出订单之前必然有买入订单)
        final HashMap<String, TradingRecord> unfinishedMap = new HashMap<>(16);
        final ArrayList<TradingRecord> finishedRecords = new ArrayList<>();
        historyOrder.forEach(orderInfo -> {
            // 需要将多组买入和
            if ("买入".equals(orderInfo.getType())) {
                final TradingRecord tradingRecord = unfinishedMap.getOrDefault(orderInfo.getCode(), getInitTradingRecord());
                fixPropsFromBuyOrder(tradingRecord, orderInfo);
                unfinishedMap.put(tradingRecord.getCode(), tradingRecord);
            }
            if ("卖出".equals(orderInfo.getType())) {
                // 尝试找到之前的买入记录
                final TradingRecord tradingRecord = unfinishedMap.getOrDefault(orderInfo.getCode(), getInitTradingRecord());
                fixPropsFromSaleOrder(tradingRecord, orderInfo);
                // 如果数据完整，转移到到另外一个完整数据集合
                if (tradingRecord.getSaleNumber().equals(tradingRecord.getBuyNumber())) {
                    // 计算收益金额
                    double income = tradingRecord.getSaleAmount() - tradingRecord.getBuyAmount();
                    double incomeRate = income / tradingRecord.getBuyAmount();
                    long holdDays = DateUtils.diff(tradingRecord.getSaleDate(), tradingRecord.getBuyDate());
                    double dailyIncomeRate = holdDays == 0 ? 0 : incomeRate / holdDays;
                    tradingRecord.setIncome(income);
                    tradingRecord.setIncomeRate(incomeRate);
                    tradingRecord.setHoldDays((int) holdDays);
                    tradingRecord.setDailyIncomeRate(dailyIncomeRate);
                    tradingRecord.setSold("1");
                    finishedRecords.add(tradingRecord);
                    unfinishedMap.remove(tradingRecord.getCode());
                }
            }
        });
        finishedRecords.addAll(unfinishedMap.values());
        log.info("共组装成{}条完整交易记录，{}条未完成订单", finishedRecords.size(), unfinishedMap.size());
        return finishedRecords;
    }

    private TradingRecord getInitTradingRecord() {
        final TradingRecord tradingRecord = new TradingRecord();
        tradingRecord.setBuyNumber(0.0);
        tradingRecord.setBuyAmount(0.0);
        return tradingRecord;
    }

    @SneakyThrows
    private void fixPropsFromBuyOrder(TradingRecord record, OrderInfo order) {
        record.setCode(order.getCode());
        record.setBuyDate(DateUtils.dateFormat.parse(order.getDate()));
        record.setBuyDateString(order.getDate());
        // 多个订单组合price会被最新的覆盖
        record.setBuyPrice(order.getPrice());
        record.setBuyNumber(record.getBuyNumber() + order.getNumber());
        Double amount = order.getPrice() * order.getNumber();
        Double buyAmount = amount + stockService.getPeeAmount(amount);
        record.setBuyAmount(record.getBuyAmount() + buyAmount);
        record.setBuyNo(order.getAnswerNo());
        record.setName(order.getName());
        record.setCode(order.getCode());
        record.setBuyNo(order.getAnswerNo());
        record.setSold("0");
    }

    @SneakyThrows
    private void fixPropsFromSaleOrder(TradingRecord record, OrderInfo order) {
        record.setSaleDate(DateUtils.dateFormat.parse(order.getDate()));
        record.setSaleDateString(order.getDate());
        record.setSalePrice(order.getPrice());
        Double saleNumber = order.getNumber();
        if (Objects.nonNull(record.getSaleNumber())) saleNumber += order.getNumber();
        record.setSaleNumber(saleNumber);
        Double amount = order.getPrice() * saleNumber;
        Double saleAmount = amount - stockService.getPeeAmount(amount);
        if (Objects.nonNull(record.getSaleAmount())) saleAmount += record.getSaleAmount();
        record.setSaleAmount(saleAmount);
        record.setSaleNo(order.getAnswerNo());
        record.setSold("1");
    }
}
