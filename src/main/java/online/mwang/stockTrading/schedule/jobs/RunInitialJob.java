package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IStockService;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockPrices;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.TradingRecordMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
public class RunInitialJob extends BaseJob {

    private final IStockService dataService;
    private final MongoTemplate mongoTemplate;
    private final OrderInfoService orderInfoService;
    private final TradingRecordMapper tradingRecordMapper;
    private final TradingRecordService tradingRecordService;

    @Override
    public void run() {
        initHistoryOrder();
        initHistoryPriceData();
    }

    private void initHistoryPriceData() {
        // 首次初始化执行，写入4000支股票，每只股票约500条数据
        List<StockInfo> stockInfoList = dataService.getDataList();
        stockInfoList.forEach(s -> {
            Query query = new Query(Criteria.where("code").is(s.getCode()));
            List<StockPrices> find = mongoTemplate.find(query, StockPrices.class);
            if (find.size() > 0) {
                log.info("股票[{}-{}]历史数据已经存在，无需写入", s.getName(), s.getCode());
            } else {
                List<DailyItem> historyPrices = dataService.getHistoryPrices(s.getCode());
                List<StockPrices> stockPricesList = historyPrices.stream().map(item -> {
                    StockPrices stockPrices = new StockPrices();
                    stockPrices.setName(s.getName());
                    stockPrices.setCode(s.getCode());
                    stockPrices.setDate(item.getDate());
                    stockPrices.setPrice1(item.getPrice1());
                    stockPrices.setPrice2(item.getPrice2());
                    stockPrices.setPrice3(item.getPrice3());
                    stockPrices.setPrice4(item.getPrice4());
                    return stockPrices;
                }).collect(Collectors.toList());
                mongoTemplate.insert(stockPricesList, StockPrices.class);
                log.info("股票[{}-{}]，{}条历史数据写入完成！", s.getName(), s.getCode(), stockPricesList.size());
            }
        });
        log.info("共写入了{}支股票历史数据", stockInfoList.size());
    }

    @Deprecated
    private void initHistoryOrder() {
        // 初始化订单数据，当交易记录数据丢失，或者在证券平台上已有订单数据，需要同步
        // 将数据写入到TradingRecord 和 OrderInfo表
        final List<OrderInfo> historyOrders = dataService.getHistoryOrder();
        final List<OrderInfo> todayOrders = dataService.getTodayOrder();
        historyOrders.addAll(todayOrders);
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
        final List<TradingRecord> findList = tradingRecordMapper.selectList(queryWrapper);
        return findList.size() > 0;
    }


    private void fixOrderProps(OrderInfo orderInfo) {
        orderInfo.setStatus("1");
        double amount = orderInfo.getNumber() * orderInfo.getPrice();
        Double peer = dataService.getPeeAmount(amount);
        String type = orderInfo.getType();
        orderInfo.setAmount(type.equals("卖出") ? amount - peer : amount + peer);
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
        Double buyAmount = amount + dataService.getPeeAmount(amount);
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
        Double saleAmount = amount - dataService.getPeeAmount(amount);
        if (Objects.nonNull(record.getSaleAmount())) saleAmount += record.getSaleAmount();
        record.setSaleAmount(saleAmount);
        record.setSaleNo(order.getAnswerNo());
        record.setSold("1");
    }
}
