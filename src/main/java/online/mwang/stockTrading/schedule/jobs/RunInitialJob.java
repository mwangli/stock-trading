package online.mwang.stockTrading.schedule.jobs;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.model.IModelService;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.dto.DailyItem;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.mapper.TradingRecordMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    private final IDataService dataService;
    private final AccountInfoMapper accountInfoMapper;
    private final IModelService modelService;
    private final MongoTemplate mongoTemplate;
    private final StockInfoService stockInfoService;
    private final OrderInfoService orderInfoService;
    private final TradingRecordMapper tradingRecordMapper;
    private final TradingRecordService tradingRecordService;

    @Override
    public void run() {
//        initHistoryOrder();
        initHistoryPriceData();
    }

    private void initHistoryOrder() {
        // 初始化订单数据，当交易记录数据丢失，或者在证券平台上已有订单数据，需要同步
        // 将数据写入到TradingRecord 和 OrderInfo表
        final List<OrderInfo> historyOrders = dataService.getHistoryOrder();
        final List<OrderInfo> todayOrders = dataService.getTodayOrder();
        historyOrders.addAll(todayOrders);
        final List<TradingRecord> tradingRecords = buildTradingRecord(historyOrders);
        // 写入时根据买入股票编号，交易日期来判断是否已经存在
        tradingRecords.forEach(this::fixProps);
        final List<TradingRecord> saveList = tradingRecords.stream().filter(r -> !isExist(r)).collect(Collectors.toList());
        tradingRecordService.saveBatch(saveList);
        log.info("共写入{}条完整交易记录。", saveList.size());
    }

    private boolean isExist(TradingRecord record) {
        final LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TradingRecord::getCode, record.getCode());
        queryWrapper.eq(TradingRecord::getBuyDateString, record.getBuyDateString());
        queryWrapper.eq(TradingRecord::getSaleDateString, record.getSaleDateString());
        final List<TradingRecord> findList = tradingRecordMapper.selectList(queryWrapper);
        return findList.size() > 0;
    }

    private void fixProps(TradingRecord tradingRecord) {
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
                // 如果数据完整，转移到到另外一个完整数据集合,并移除当前Map
                if (Math.abs(tradingRecord.getSaleNumber()) == Math.abs(tradingRecord.getBuyNumber())) {
                    finishedRecords.add(tradingRecord);
                    unfinishedMap.remove(tradingRecord.getCode());
                }
            }
        });
        log.info("共组装成{}条交易记录，剩余{}条未完成订单", finishedRecords.size(), unfinishedMap.size());
        return finishedRecords;
    }

    private TradingRecord getInitTradingRecord() {
        final TradingRecord tradingRecord = new TradingRecord();
        tradingRecord.setBuyNumber(0.0);
        tradingRecord.setBuyAmount(0.0);
        tradingRecord.setSaleNumber(0.0);
        tradingRecord.setSaleAmount(0.0);
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
        final double amount = order.getPrice() * order.getNumber();
        // 买入金额中包含了手续费
        final double buyAmount = amount + dataService.getPeeAmount(amount);
        record.setBuyAmount(record.getBuyAmount() + buyAmount);
        record.setSold("0");
    }

    @SneakyThrows
    private void fixPropsFromSaleOrder(TradingRecord record, OrderInfo order) {
        record.setSaleDate(DateUtils.dateFormat.parse(order.getDate()));
        record.setSaleDateString(order.getDate());
        record.setSalePrice(order.getPrice());
        record.setSaleNumber(order.getNumber());
        final double amount = order.getPrice() * order.getNumber();
        // 卖出金额中去除了手续费
        final double saleAmount = amount - dataService.getPeeAmount(amount);
        record.setBuyAmount(saleAmount);
        record.setSold("1");
    }


    private void initHistoryPriceData() {
        // 首次初始化执行，写入4000支股票，每只股票约500条数据
        LambdaQueryWrapper<StockInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockInfo::getDeleted, 1);
        List<StockInfo> stockInfoList = stockInfoService.list(queryWrapper);
        stockInfoList.forEach(s -> {
            List<DailyItem> historyPrices = dataService.getHistoryPrices(s.getCode());
            List<StockHistoryPrice> stockHistoryPrices = historyPrices.stream().map(item -> {
                StockHistoryPrice stockHistoryPrice = new StockHistoryPrice();
                stockHistoryPrice.setName(s.getName());
                stockHistoryPrice.setCode(s.getCode());
                stockHistoryPrice.setDate(item.getDate());
                stockHistoryPrice.setPrice1(item.getPrice1());
                stockHistoryPrice.setPrice2(item.getPrice2());
                stockHistoryPrice.setPrice3(item.getPrice3());
                stockHistoryPrice.setPrice4(item.getPrice4());
                return stockHistoryPrice;
            }).collect(Collectors.toList());
            mongoTemplate.insert(stockHistoryPrices, StockHistoryPrice.class);
            log.info("股票[{}-{}]，{}条历史数据写入完成！", s.getName(), s.getCode(), stockHistoryPrices.size());
        });
        log.info("共写入了{}支股票历史数据", stockInfoList.size());
    }
}
